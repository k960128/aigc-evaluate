package com.kant.llm.eval.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kant.llm.eval.client.ModelClientStrategy;
import com.kant.llm.eval.client.ModelClientStrategyFactory;
import com.kant.llm.eval.client.ModelInfo;
import com.kant.llm.eval.client.ModelRequest;
import com.kant.llm.eval.client.ModelResponse;
import com.kant.llm.eval.common.convention.EvalContext;
import com.kant.llm.eval.common.enums.EvalResultStatusEnums;
import com.kant.llm.eval.common.enums.L2DecisionTypeEnums;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.common.enums.PipelineNodeCodeEnums;
import com.kant.llm.eval.common.enums.PipelineNodeStatusEnums;
import com.kant.llm.eval.common.enums.TaskStatusEnums;
import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import com.kant.llm.eval.common.exception.SecurityBlockException;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.dao.mapper.ModelInfoMapper;
import com.kant.llm.eval.engine.L1InterceptionEngine;
import com.kant.llm.eval.mq.EvalMqTopics;
import com.kant.llm.eval.mq.message.EvalSampleExecutionMessage;
import com.kant.llm.eval.service.EvalPipelineNodeRecorder;
import com.kant.llm.eval.service.l2.L2EvaluationService;
import com.kant.llm.eval.service.l2.model.L2EvaluationRequest;
import com.kant.llm.eval.service.l2.model.L2EvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 单样本执行消费者。
 *
 * <p>该消费者承接任务拆分后的 Execution_MQ 消息，负责完成“调用被测模型 -> L1 安全判定
 * -> L2 双路召回判定 -> 回写单条结果 -> 推进批次进度”的执行闭环。L3 尚未接入时，
 * L2 模糊区会先进入人工核验状态。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.EXECUTION,
        consumerGroup = "aigc-eval-sample-execution-consumer-group"
)
public class EvalSampleExecutionConsumer implements RocketMQListener<EvalSampleExecutionMessage> {

    private static final String EXECUTION_LOCK_KEY_PREFIX = "aigc-eval:eval-result:execute:";

    /**
     * MQ 重投时无需再次处理的终态状态。
     */
    private static final List<Integer> FINISHED_RESULT_STATUS = List.of(
            EvalResultStatusEnums.AUTO_SCORED.getCode(),
            EvalResultStatusEnums.MANUAL_REVIEWED.getCode(),
            EvalResultStatusEnums.FAILED.getCode(),
            EvalResultStatusEnums.STOPPED.getCode()
    );

    private final EvalResultDetailMapper evalResultDetailMapper;
    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final ModelClientStrategyFactory modelClientStrategyFactory;
    private final L1InterceptionEngine l1InterceptionEngine;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final EvalPipelineNodeRecorder evalPipelineNodeRecorder;
    private final L2EvaluationService l2EvaluationService;

    public EvalSampleExecutionConsumer(EvalResultDetailMapper evalResultDetailMapper,
                                       EvalTaskDetailMapper evalTaskDetailMapper,
                                       ModelInfoMapper modelInfoMapper,
                                       ModelClientStrategyFactory modelClientStrategyFactory,
                                       L1InterceptionEngine l1InterceptionEngine,
                                       RedissonClient redissonClient,
                                       TransactionTemplate transactionTemplate,
                                       EvalPipelineNodeRecorder evalPipelineNodeRecorder,
                                       L2EvaluationService l2EvaluationService) {
        this.evalResultDetailMapper = evalResultDetailMapper;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.modelClientStrategyFactory = modelClientStrategyFactory;
        this.l1InterceptionEngine = l1InterceptionEngine;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.evalPipelineNodeRecorder = evalPipelineNodeRecorder;
        this.l2EvaluationService = l2EvaluationService;
    }

    @Override
    public void onMessage(EvalSampleExecutionMessage message) {
        log.info("开始消费单样本执行 MQ 消息，topic: {}, taskDetailId: {}, resultDetailId: {}, sampleId: {}, modelId: {}",
                EvalMqTopics.EXECUTION, message.getTaskDetailId(), message.getResultDetailId(),
                message.getSampleId(), message.getModelId());
        RLock lock = redissonClient.getLock(EXECUTION_LOCK_KEY_PREFIX + message.getResultDetailId());
        boolean locked;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("评测样本执行被中断", ex, BaseErrorCode.SERVICE_ERROR);
        }
        if (!locked) {
            log.info("评测结果明细正在执行中，跳过并发重复消息，resultDetailId: {}", message.getResultDetailId());
            return;
        }
        log.info("单样本执行锁获取成功，resultDetailId: {}", message.getResultDetailId());
        try {
            EvalResultDetailDO resultDetail = evalResultDetailMapper.selectById(message.getResultDetailId());
            if (resultDetail == null) {
                log.warn("评测结果明细不存在，跳过单样本执行，消息: {}", message);
                return;
            }
            // MQ 至少一次投递，终态结果直接跳过，避免重复调用模型或重复累加批次进度。
            if (FINISHED_RESULT_STATUS.contains(resultDetail.getStatus())) {
                log.info("评测结果明细已处理完成，跳过重复执行，resultDetailId: {}", resultDetail.getId());
                return;
            }

            try {
                EvalResultDetailDO successResult = executeSample(message, resultDetail);
                markResultSuccessAndIncreaseProgress(successResult, message.getTaskDetailId());
                log.info("单样本执行 MQ 消息处理完成，taskDetailId: {}, resultDetailId: {}",
                        message.getTaskDetailId(), message.getResultDetailId());
            } catch (Exception ex) {
                log.error("评测样本执行失败，消息: {}", message, ex);
                markResultFailedAndIncreaseProgress(resultDetail.getId(), message.getTaskDetailId(), ex);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行单条样本的核心业务流程。
     *
     * <p>先把批次状态从 READY 推进到 RUNNING，再调用模型和 L1 判定。
     * 这样即使批次下多个样本并发执行，也能稳定表达“该批次已开始执行”。</p>
     */
    private EvalResultDetailDO executeSample(EvalSampleExecutionMessage message, EvalResultDetailDO resultDetail) {
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(message.getTaskDetailId());
        if (taskDetail == null) {
            throw new ServiceException("评测任务批次不存在，无法执行样本");
        }
        // 用户停止批次后，积压或重复到达的执行消息必须直接跳过，不能再调用被测模型。
        if (TaskStatusEnums.STOPPED.getCode().equals(taskDetail.getStatus())) {
            log.info("评测任务批次已终止，跳过样本执行，taskDetailId: {}, resultDetailId: {}",
                    taskDetail.getId(), resultDetail.getId());
            Long stoppedNodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                    PipelineNodeCodeEnums.MODEL_CALL, buildStoppedInputSnapshot("TASK_STOPPED_BEFORE_MODEL_CALL"));
            evalPipelineNodeRecorder.stopNode(stoppedNodeRecordId, "评测任务批次已终止，跳过模型调用",
                    buildStoppedNodeResult("TASK_STOPPED_BEFORE_MODEL_CALL"));
            return null;
        }
        markTaskRunning(taskDetail);
        log.info("评测样本开始调用被测模型，taskDetailId: {}, resultDetailId: {}, modelId: {}",
                taskDetail.getId(), resultDetail.getId(), message.getModelId());

        ModelInfo modelInfo = loadModelInfo(message.getModelId());
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(modelInfo);
        Long modelCallNodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                PipelineNodeCodeEnums.MODEL_CALL, buildModelCallInputSnapshot(modelInfo, resultDetail));
        ModelResponse modelResponse;
        try {
            // 开始调用大模型
            modelResponse = strategy.call(ModelRequest.builder()
                    .modelInfo(modelInfo)
                    .inputText(resultDetail.getInputText())
                    .build());
            evalPipelineNodeRecorder.finishNode(modelCallNodeRecordId, PipelineNodeStatusEnums.PASSED,
                    buildModelCallOutputSnapshot(modelResponse), buildModelCallNodeResult(modelResponse), null);
        } catch (Exception ex) {
            evalPipelineNodeRecorder.failNode(modelCallNodeRecordId, ex, null, buildFailedNodeResult("MODEL_CALL_FAILED"));
            throw ex;
        }
        log.info("评测样本模型调用完成，taskDetailId: {}, resultDetailId: {}, modelId: {}, elapsed: {}",
                taskDetail.getId(), resultDetail.getId(), message.getModelId(), modelResponse.getElapsed());
        // 模型调用无法强制中断，返回后先确认批次状态；若用户已停止，后续 L1 节点不再执行。
        if (isTaskStopped(message.getTaskDetailId())) {
            Long l1StoppedNodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                    PipelineNodeCodeEnums.L1, buildStoppedInputSnapshot("TASK_STOPPED_BEFORE_L1"));
            evalPipelineNodeRecorder.stopNode(l1StoppedNodeRecordId, "评测任务批次已终止，跳过 L1 判定",
                    buildStoppedNodeResult("TASK_STOPPED_BEFORE_L1"));
            log.info("评测任务批次已在模型调用期间被终止，丢弃模型返回结果，taskDetailId: {}, resultDetailId: {}",
                    message.getTaskDetailId(), resultDetail.getId());
            return null;
        }

        EvalResultDetailDO updateEntity = EvalResultDetailDO.builder()
                .id(resultDetail.getId())
                .modelOutput(modelResponse.getRespContent())
                .rawResponse(JSON.toJSONString(modelResponse))
                .latency(toIntegerLatency(modelResponse.getElapsed()))
                .status(EvalResultStatusEnums.AUTO_SCORED.getCode())
                .build();
        // L1 层拦截；命中高确定性风险时直接短路，不再进入 L2。
        L1JudgementResult l1JudgementResult = applyL1Judgement(taskDetail, resultDetail, updateEntity, modelResponse.getRespContent());
        // L1 判定完成后再次确认批次状态；若用户已停止，丢弃本次结果，不推进样本最终态。
        if (isTaskStopped(message.getTaskDetailId())) {
            log.info("评测任务批次已在模型调用期间被终止，丢弃模型返回结果，taskDetailId: {}, resultDetailId: {}",
                    message.getTaskDetailId(), resultDetail.getId());
            return null;
        }
        // L2 只在 L1 未拦截时执行：
        // 1. L1 已经命中高确定性黑词时，样本已经有明确违规结论，继续召回会造成重复判定。
        // 2. L1 未拦截但命中 warning 标签时，warningTags 会作为 L2 查询上下文，帮助召回相邻风险小类。
        // 3. L2 的三种结果分别对应：高置信违规、低风险安全、模糊区人工核验。
        if (!l1JudgementResult.blocked()) {
            applyL2Judgement(taskDetail, resultDetail, updateEntity, l1JudgementResult.warningTags(), modelResponse.getRespContent());
        }
        return updateEntity;
    }

    /**
     * 加载并组装模型调用所需的运行时配置。
     *
     * <p>数据库中存储的是持久化模型实体，策略工厂需要的是轻量级 ModelInfo。
     * 这里集中完成厂商枚举转换，避免消费者主流程掺杂配置细节。</p>
     */
    private ModelInfo loadModelInfo(Long modelId) {
        ModelInfoDO modelInfoDO = modelInfoMapper.selectById(modelId);
        if (modelInfoDO == null) {
            throw new ServiceException("模型配置不存在，无法执行评测样本");
        }
        ModelManufacturerEnum manufacturerType;
        try {
            manufacturerType = ModelManufacturerEnum.valueOf(modelInfoDO.getManufacturerCode().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ServiceException("不支持的大模型厂商标识：" + modelInfoDO.getManufacturerCode(),
                    ex, BaseErrorCode.SERVICE_ERROR);
        }
        return ModelInfo.builder()
                .modelId(modelInfoDO.getId())
                .model(modelInfoDO.getModel())
                .apiKey(modelInfoDO.getApiKey())
                .baseUrl(modelInfoDO.getBaseUrl())
                .manufacturerType(manufacturerType)
                .build();
    }

    /**
     * 执行 L1 字面量安全判定。
     *
     * <p>L1 命中一级风险词时，AC 引擎会抛出 SecurityBlockException。
     * 未命中一级风险词时，会把 warning 标签继续传给 L2 作为召回上下文。</p>
     */
    private L1JudgementResult applyL1Judgement(EvalTaskDetailDO taskDetail,
                                               EvalResultDetailDO resultDetail,
                                               EvalResultDetailDO updateEntity,
                                               String modelOutput) {
        Long l1NodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                PipelineNodeCodeEnums.L1, buildL1InputSnapshot(modelOutput));
        try {
            EvalContext context = l1InterceptionEngine.analyze(modelOutput);
            updateEntity.setIsSafe(!context.isL1Blocked());
            if (context.isL1Blocked()) {
                updateEntity.setErrorMsg(buildL1BlockMessage(context.getL1BlockDetailsId(), context.getL1BlockKeyword()));
                evalPipelineNodeRecorder.finishNode(l1NodeRecordId, PipelineNodeStatusEnums.BLOCKED,
                        buildL1OutputSnapshot(context), buildL1NodeResult(context, "BLOCKED"), updateEntity.getErrorMsg());
                log.info("评测样本 L1 判定命中拦截，resultDetailId: {}, riskDetailsId: {}, keyword: {}",
                        updateEntity.getId(), context.getL1BlockDetailsId(), context.getL1BlockKeyword());
                return new L1JudgementResult(true, context.getHitWarningTags());
            } else {
                evalPipelineNodeRecorder.finishNode(l1NodeRecordId, PipelineNodeStatusEnums.PASSED,
                        buildL1OutputSnapshot(context), buildL1NodeResult(context, "PASSED"), null);
                log.info("评测样本 L1 判定通过，resultDetailId: {}", updateEntity.getId());
                return new L1JudgementResult(false, context.getHitWarningTags());
            }
        } catch (SecurityBlockException ex) {
            updateEntity.setIsSafe(false);
            updateEntity.setErrorMsg(buildL1BlockMessage(ex.getRiskDetailsId(), ex.getKeyword()));
            evalPipelineNodeRecorder.finishNode(l1NodeRecordId, PipelineNodeStatusEnums.BLOCKED,
                    null, buildL1BlockedExceptionNodeResult(ex), updateEntity.getErrorMsg());
            log.info("评测样本 L1 判定抛出拦截异常，resultDetailId: {}, riskDetailsId: {}, keyword: {}",
                    updateEntity.getId(), ex.getRiskDetailsId(), ex.getKeyword());
            return new L1JudgementResult(true, List.of());
        } catch (Exception ex) {
            evalPipelineNodeRecorder.failNode(l1NodeRecordId, ex, null, buildFailedNodeResult("L1_FAILED"));
            throw ex;
        }
    }

    /**
     * 执行 L2 双路召回判定。
     *
     * <p>L2 会记录独立流水线节点。高置信风险直接拦截，低风险安全自动完成，
     * 模糊区在 L3 未接入前先标记为人工核验。</p>
     */
    private void applyL2Judgement(EvalTaskDetailDO taskDetail,
                                  EvalResultDetailDO resultDetail,
                                  EvalResultDetailDO updateEntity,
                                  List<Long> l1WarningTags,
                                  String modelOutput) {
        // L2 作为独立流水线节点记录，原因是它有完整的召回证据、阈值快照和路由结果。
        // 即使最终样本只写一个 is_safe/status，排查时仍可从 eval_pipeline_node_detail 回放本次判定细节。
        Long l2NodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                PipelineNodeCodeEnums.L2, buildL2InputSnapshot(resultDetail, modelOutput, l1WarningTags));
        try {
            L2EvaluationResult l2Result = l2EvaluationService.evaluate(L2EvaluationRequest.builder()
                    .taskId(taskDetail.getTaskId())
                    .taskDetailId(taskDetail.getId())
                    .resultDetailId(resultDetail.getId())
                    .sampleId(resultDetail.getSampleId())
                    .inputText(resultDetail.getInputText())
                    .modelOutput(modelOutput)
                    .l1WarningTags(l1WarningTags)
                    .build());
            updateEntity.setIsSafe(l2Result.getSafe());
            updateEntity.setErrorMsg(buildL2ResultMessage(l2Result));
            // L3 尚未接入，本阶段把 PASS_TO_L3 映射到人工核验终态。
            // 这里仍然推进批次 finished_count，避免模糊样本卡住整个批次。
            if (l2Result.getDecisionType() == L2DecisionTypeEnums.PASS_TO_L3) {
                updateEntity.setStatus(EvalResultStatusEnums.MANUAL_REVIEWED.getCode());
            } else {
                updateEntity.setStatus(EvalResultStatusEnums.AUTO_SCORED.getCode());
            }
            // 流水线节点状态只表达 L2 节点是否放行：
            // - safe=false：L2 已阻断，节点记 BLOCKED。
            // - safe=true 或 safe=null：节点本身执行成功，记 PASSED；safe=null 的人工核验信息在 node_result 中体现。
            PipelineNodeStatusEnums nodeStatus = l2Result.getSafe() != null && !l2Result.getSafe()
                    ? PipelineNodeStatusEnums.BLOCKED
                    : PipelineNodeStatusEnums.PASSED;
            evalPipelineNodeRecorder.finishNode(l2NodeRecordId, nodeStatus,
                    l2Result.getOutputSnapshot(), l2Result.getNodeResult(), null);
            log.info("评测样本 L2 判定完成，resultDetailId: {}, decision: {}, safe: {}",
                    resultDetail.getId(), l2Result.getDecisionType().getCode(), l2Result.getSafe());
        } catch (Exception ex) {
            evalPipelineNodeRecorder.failNode(l2NodeRecordId, ex, null, buildFailedNodeResult("L2_FAILED"));
            throw ex;
        }
    }

    /**
     * 只有 READY 状态可以被推进到 RUNNING，避免重复消费把 COMPLETED/ERROR 批次误改回运行中。
     */
    private void markTaskRunning(EvalTaskDetailDO taskDetail) {
        evalTaskDetailMapper.update(null, new LambdaUpdateWrapper<EvalTaskDetailDO>()
                .eq(EvalTaskDetailDO::getId, taskDetail.getId())
                .eq(EvalTaskDetailDO::getStatus, TaskStatusEnums.READY.getCode())
                .set(EvalTaskDetailDO::getStatus, TaskStatusEnums.RUNNING.getCode())
                .set(EvalTaskDetailDO::getStartTime, taskDetail.getStartTime() == null ? LocalDateTime.now() : taskDetail.getStartTime()));
    }

    /**
     * 单样本完成后推进批次进度。
     *
     * <p>计数使用 SQL 原子递增，避免一个批次内多个样本并发完成时发生覆盖写。
     * 计数更新后再回查批次，判断是否已经全部完成。</p>
     */
    private void increaseTaskProgress(Long taskDetailId, boolean failed) {
        UpdateWrapper<EvalTaskDetailDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", taskDetailId)
                .setSql("finished_count = COALESCE(finished_count, 0) + 1");
        if (failed) {
            updateWrapper.setSql("failed_count = COALESCE(failed_count, 0) + 1");
        }
        evalTaskDetailMapper.update(null, updateWrapper);
        log.info("评测任务批次进度已推进，taskDetailId: {}, failedIncrement: {}", taskDetailId, failed);
        refreshTaskFinalStatus(taskDetailId);
    }

    /**
     * 批次内所有样本都完成后，根据失败数写入最终批次状态。
     */
    private void refreshTaskFinalStatus(Long taskDetailId) {
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(taskDetailId);
        if (taskDetail == null || taskDetail.getTotalCount() == null || taskDetail.getFinishedCount() == null) {
            return;
        }
        if (taskDetail.getFinishedCount() < taskDetail.getTotalCount()) {
            log.info("评测任务批次尚未全部完成，taskDetailId: {}, finishedCount: {}, totalCount: {}, failedCount: {}",
                    taskDetailId, taskDetail.getFinishedCount(), taskDetail.getTotalCount(), taskDetail.getFailedCount());
            return;
        }
        Integer failedCount = taskDetail.getFailedCount() == null ? 0 : taskDetail.getFailedCount();
        Integer finalStatus = Objects.equals(failedCount, 0)
                ? TaskStatusEnums.COMPLETED.getCode()
                : TaskStatusEnums.ERROR.getCode();
        evalTaskDetailMapper.update(null, new LambdaUpdateWrapper<EvalTaskDetailDO>()
                .eq(EvalTaskDetailDO::getId, taskDetailId)
                .in(EvalTaskDetailDO::getStatus, TaskStatusEnums.READY.getCode(), TaskStatusEnums.RUNNING.getCode())
                .set(EvalTaskDetailDO::getStatus, finalStatus)
                .set(EvalTaskDetailDO::getEndTime, LocalDateTime.now()));
        log.info("评测任务批次执行完成，taskDetailId: {}, finalStatus: {}, finishedCount: {}, totalCount: {}, failedCount: {}",
                taskDetailId, finalStatus, taskDetail.getFinishedCount(), taskDetail.getTotalCount(), failedCount);
    }

    /**
     * 成功结果和批次计数放在同一个短事务内提交。
     *
     * <p>模型调用已经在事务外完成，这里只处理数据库最终态；如果终态更新失败，
     * 整个计数推进会回滚，等待 MQ 重试。</p>
     */
    private void markResultSuccessAndIncreaseProgress(EvalResultDetailDO updateEntity, Long taskDetailId) {
        if (updateEntity == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (isTaskStopped(taskDetailId)) {
                log.info("评测任务批次已终止，跳过成功结果落库和进度推进，taskDetailId: {}, resultDetailId: {}",
                        taskDetailId, updateEntity.getId());
                return;
            }
            int updatedRows = updateResultSuccess(updateEntity);
            if (updatedRows > 0) {
                increaseTaskProgress(taskDetailId, false);
                log.info("评测样本成功结果落库完成，taskDetailId: {}, resultDetailId: {}",
                        taskDetailId, updateEntity.getId());
            } else {
                log.info("评测样本成功结果未更新，可能已被重复消息处理，taskDetailId: {}, resultDetailId: {}",
                        taskDetailId, updateEntity.getId());
            }
        });
    }

    /**
     * 查询批次是否已经被用户主动终止。
     */
    private boolean isTaskStopped(Long taskDetailId) {
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(taskDetailId);
        return taskDetail == null || TaskStatusEnums.STOPPED.getCode().equals(taskDetail.getStatus());
    }

    /**
     * 失败结果和批次计数放在同一个短事务内提交。
     */
    private void markResultFailedAndIncreaseProgress(Long resultDetailId, Long taskDetailId, Exception ex) {
        EvalResultDetailDO updateEntity = EvalResultDetailDO.builder()
                .id(resultDetailId)
                .status(EvalResultStatusEnums.FAILED.getCode())
                .isSafe(false)
                .errorMsg(trimErrorMessage(ex.getMessage()))
                .build();
        transactionTemplate.executeWithoutResult(status -> {
            if (isTaskStopped(taskDetailId)) {
                log.info("评测任务批次已终止，跳过失败结果落库和进度推进，taskDetailId: {}, resultDetailId: {}",
                        taskDetailId, resultDetailId);
                return;
            }
            int updatedRows = updateResultFailed(updateEntity);
            if (updatedRows > 0) {
                increaseTaskProgress(taskDetailId, true);
                log.info("评测样本失败结果落库完成，taskDetailId: {}, resultDetailId: {}",
                        taskDetailId, resultDetailId);
            } else {
                log.info("评测样本失败结果未更新，可能已被重复消息处理，taskDetailId: {}, resultDetailId: {}",
                        taskDetailId, resultDetailId);
            }
        });
    }

    /**
     * 条件更新成功结果，只有非终态结果才能被写成终态。
     */
    private int updateResultSuccess(EvalResultDetailDO updateEntity) {
        UpdateWrapper<EvalResultDetailDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", updateEntity.getId())
                .notIn("status", FINISHED_RESULT_STATUS)
                .set("model_output", updateEntity.getModelOutput())
                .set("raw_response", updateEntity.getRawResponse())
                .set("latency", updateEntity.getLatency())
                // L2 模糊区需要显式写入 null，表示当前安全结论等待人工核验或后续 L3。
                .set("is_safe", updateEntity.getIsSafe())
                .set("error_msg", updateEntity.getErrorMsg())
                .set("status", updateEntity.getStatus());
        return evalResultDetailMapper.update(null, updateWrapper);
    }

    /**
     * 条件更新失败结果，避免重复消息把同一条结果重复计入失败。
     */
    private int updateResultFailed(EvalResultDetailDO updateEntity) {
        return evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getId, updateEntity.getId())
                .notIn(EvalResultDetailDO::getStatus, FINISHED_RESULT_STATUS)
                .set(EvalResultDetailDO::getIsSafe, updateEntity.getIsSafe())
                .set(EvalResultDetailDO::getErrorMsg, updateEntity.getErrorMsg())
                .set(EvalResultDetailDO::getStatus, updateEntity.getStatus()));
    }

    /**
     * 将模型耗时转成结果表使用的 Integer 类型，超出范围时取最大值，避免类型溢出。
     */
    private Integer toIntegerLatency(Long elapsed) {
        if (elapsed == null) {
            return null;
        }
        return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : elapsed.intValue();
    }

    /**
     * 构造 L1 拦截原因，后续可替换为结构化风险字段。
     */
    private String buildL1BlockMessage(Long riskDetailsId, String keyword) {
        return "L1安全拦截命中，riskDetailsId=" + riskDetailsId + "，keyword=" + keyword;
    }

    /**
     * 构造模型调用节点输入快照。
     */
    private Object buildModelCallInputSnapshot(ModelInfo modelInfo, EvalResultDetailDO resultDetail) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelId", modelInfo.getModelId());
        snapshot.put("manufacturerType", modelInfo.getManufacturerType());
        snapshot.put("model", modelInfo.getModel());
        snapshot.put("inputText", resultDetail.getInputText());
        return snapshot;
    }

    /**
     * 构造模型调用节点输出快照。
     */
    private Object buildModelCallOutputSnapshot(ModelResponse modelResponse) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelId", modelResponse.getModelId());
        snapshot.put("respContent", modelResponse.getRespContent());
        snapshot.put("elapsed", modelResponse.getElapsed());
        return snapshot;
    }

    /**
     * 构造模型调用节点结构化结果。
     */
    private Object buildModelCallNodeResult(ModelResponse modelResponse) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("success", true);
        nodeResult.put("elapsed", modelResponse.getElapsed());
        return nodeResult;
    }

    /**
     * 构造 L1 节点输入快照。
     */
    private Object buildL1InputSnapshot(String modelOutput) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelOutput", modelOutput);
        return snapshot;
    }

    /**
     * 构造 L1 节点输出快照。
     */
    private Object buildL1OutputSnapshot(EvalContext context) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("l1Blocked", context.isL1Blocked());
        snapshot.put("l1BlockDetailsId", context.getL1BlockDetailsId());
        snapshot.put("l1BlockKeyword", context.getL1BlockKeyword());
        snapshot.put("hitWarningTags", context.getHitWarningTags());
        return snapshot;
    }

    /**
     * 构造 L1 节点结构化结果。
     */
    private Object buildL1NodeResult(EvalContext context, String decision) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("blocked", context.isL1Blocked());
        nodeResult.put("riskDetailsId", context.getL1BlockDetailsId());
        nodeResult.put("keyword", context.getL1BlockKeyword());
        nodeResult.put("warningTags", context.getHitWarningTags());
        nodeResult.put("decision", decision);
        return nodeResult;
    }

    /**
     * 构造 L1 异常拦截结构化结果。
     */
    private Object buildL1BlockedExceptionNodeResult(SecurityBlockException ex) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("blocked", true);
        nodeResult.put("riskDetailsId", ex.getRiskDetailsId());
        nodeResult.put("keyword", ex.getKeyword());
        nodeResult.put("decision", "BLOCKED");
        return nodeResult;
    }

    /**
     * 构造 L2 节点输入快照。
     */
    private Object buildL2InputSnapshot(EvalResultDetailDO resultDetail, String modelOutput, List<Long> l1WarningTags) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("inputText", resultDetail.getInputText());
        snapshot.put("modelOutput", modelOutput);
        snapshot.put("l1WarningTags", l1WarningTags);
        return snapshot;
    }

    /**
     * 构造 L2 结果说明。
     *
     * <p>结果表没有独立 L2 风险字段，第一阶段把可读路由原因放入 errorMsg，
     * 结构化证据保存在 L2 流水线节点中。</p>
     */
    private String buildL2ResultMessage(L2EvaluationResult l2Result) {
        if (l2Result == null || l2Result.getDecisionType() == null) {
            return null;
        }
        if (l2Result.getDecisionType() == L2DecisionTypeEnums.SAFE
                || l2Result.getDecisionType() == L2DecisionTypeEnums.DEGRADED_SAFE) {
            return null;
        }
        return trimErrorMessage("L2安全判定：" + l2Result.getDecisionType().getDesc()
                + "，riskDetailsId=" + l2Result.getRiskDetailsId()
                + "，reason=" + l2Result.getRouteReason());
    }

    /**
     * 构造失败节点结构化结果。
     */
    private Object buildFailedNodeResult(String decision) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("success", false);
        nodeResult.put("decision", decision);
        return nodeResult;
    }

    /**
     * 构造停止跳过节点输入快照。
     */
    private Object buildStoppedInputSnapshot(String reason) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("reason", reason);
        return snapshot;
    }

    /**
     * 构造停止跳过节点结构化结果。
     */
    private Object buildStoppedNodeResult(String decision) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("stopped", true);
        nodeResult.put("decision", decision);
        return nodeResult;
    }

    /**
     * 错误信息落库时做长度保护，避免第三方异常堆叠过长导致更新失败。
     */
    private String trimErrorMessage(String message) {
        if (message == null) {
            return "评测样本执行失败";
        }
        int maxLength = 1000;
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }

    /**
     * L1 判定轻量结果，用于判断是否继续进入 L2。
     *
     * @param blocked L1 是否已经拦截
     * @param warningTags L1 warning 风险小类标签
     */
    private record L1JudgementResult(boolean blocked, List<Long> warningTags) {
    }
}
