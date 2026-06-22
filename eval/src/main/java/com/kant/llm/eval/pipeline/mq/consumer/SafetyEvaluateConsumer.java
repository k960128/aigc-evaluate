package com.kant.llm.eval.pipeline.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kant.framework.errorcode.BaseErrorCode;
import com.kant.framework.exception.SecurityBlockException;
import com.kant.framework.exception.ServiceException;
import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.common.convention.EvalContext;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.L2DecisionTypeEnums;
import com.kant.llm.common.enums.L3DecisionTypeEnums;
import com.kant.llm.common.enums.PipelineNodeCodeEnums;
import com.kant.llm.common.enums.PipelineNodeStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.knowledge.engine.L1InterceptionEngine;
import com.kant.llm.eval.es.service.EvalEsSyncOutboxService;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import com.kant.llm.eval.pipeline.service.EvalPipelineNodeRecorder;
import com.kant.llm.eval.pipeline.service.SafetyEvaluateExecutionService;
import com.kant.llm.eval.pipeline.service.l2.L2EvaluationService;
import com.kant.llm.eval.pipeline.service.l2.model.L2EvaluationRequest;
import com.kant.llm.eval.pipeline.service.l2.model.L2EvaluationResult;
import com.kant.llm.eval.pipeline.service.l3.L3EvaluationService;
import com.kant.llm.eval.pipeline.service.l3.model.L3EvaluationRequest;
import com.kant.llm.eval.pipeline.service.l3.model.L3EvaluationResult;
import com.kant.llm.eval.risk.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.risk.dao.mapper.RiskDetailRuleMapper;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 单样本安全评测消费者。
 *
 * <p>该消费者只处理模型调用阶段已经完成的样本，也就是 eval_result_detail.status 已经进入
 * MODEL_CALLED 或 SAFETY_EVALUATING 的结果明细。这里不会再次调用被评测模型，只读取已落库
 * model_output，然后按原有顺序执行 L1、L2、L3，并在最终判定后推进批次进度。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.SAFETY_EVALUATE_TOPIC,
        consumerGroup = "aigc-eval-safety-evaluate-consumer-group"
)
public class SafetyEvaluateConsumer implements RocketMQListener<MessageWrapper<SafetyEvaluateMessage>>, SafetyEvaluateExecutionService {

    private static final String EXECUTION_LOCK_KEY_PREFIX = "aigc-eval:eval-result:execute:";
    private static final List<Integer> FINISHED_RESULT_STATUS = List.of(
            EvalResultStatusEnums.AUTO_SCORED.getCode(),
            EvalResultStatusEnums.MANUAL_REVIEWED.getCode(),
            EvalResultStatusEnums.FAILED.getCode(),
            EvalResultStatusEnums.STOPPED.getCode()
    );

    private final EvalResultDetailMapper evalResultDetailMapper;
    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final RiskDetailRuleMapper riskDetailRuleMapper;
    private final L1InterceptionEngine l1InterceptionEngine;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final EvalPipelineNodeRecorder evalPipelineNodeRecorder;
    private final L2EvaluationService l2EvaluationService;
    private final L3EvaluationService l3EvaluationService;
    private final EvalEsSyncOutboxService evalEsSyncOutboxService;

    public SafetyEvaluateConsumer(EvalResultDetailMapper evalResultDetailMapper,
                                  EvalTaskDetailMapper evalTaskDetailMapper,
                                  RiskDetailRuleMapper riskDetailRuleMapper,
                                  L1InterceptionEngine l1InterceptionEngine,
                                  RedissonClient redissonClient,
                                  TransactionTemplate transactionTemplate,
                                  EvalPipelineNodeRecorder evalPipelineNodeRecorder,
                                  L2EvaluationService l2EvaluationService,
                                  L3EvaluationService l3EvaluationService,
                                  EvalEsSyncOutboxService evalEsSyncOutboxService) {
        this.evalResultDetailMapper = evalResultDetailMapper;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.riskDetailRuleMapper = riskDetailRuleMapper;
        this.l1InterceptionEngine = l1InterceptionEngine;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.evalPipelineNodeRecorder = evalPipelineNodeRecorder;
        this.l2EvaluationService = l2EvaluationService;
        this.l3EvaluationService = l3EvaluationService;
        this.evalEsSyncOutboxService = evalEsSyncOutboxService;
    }

    @Override
    public void onMessage(MessageWrapper<SafetyEvaluateMessage> messageWrapper) {
        SafetyEvaluateMessage message = messageWrapper.getBody();
        log.info("开始消费安全评测消息，topic: {}, taskDetailId: {}, resultDetailId: {}, sampleId: {}, modelId: {}",
                EvalMqTopics.SAFETY_EVALUATE_TOPIC, message.getTaskDetailId(), message.getResultDetailId(),
                message.getSampleId(), message.getModelId());
        RLock lock = redissonClient.getLock(EXECUTION_LOCK_KEY_PREFIX + message.getResultDetailId());
        boolean locked;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("安全评测消费者获取执行锁被中断", ex, BaseErrorCode.SERVICE_ERROR);
        }
        if (!locked) {
            log.info("评测结果明细正在执行中，跳过重复安全评测消息，resultDetailId: {}", message.getResultDetailId());
            return;
        }
        try {
            evaluate(message);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void evaluate(SafetyEvaluateMessage message) {
        EvalResultDetailDO resultDetail = evalResultDetailMapper.selectById(message.getResultDetailId());
        if (resultDetail == null) {
            log.warn("评测结果明细不存在，跳过安全评测，message: {}", message);
            return;
        }
        if (FINISHED_RESULT_STATUS.contains(resultDetail.getStatus())) {
            log.info("评测结果明细已处于终态，跳过安全评测，resultDetailId: {}, status: {}",
                    resultDetail.getId(), resultDetail.getStatus());
            return;
        }
        // 安全评测只处理模型输出已落库的样本，避免 L1/L2/L3 在模型调用前误执行。
        if (!EvalResultStatusEnums.MODEL_CALLED.getCode().equals(resultDetail.getStatus())
                && !EvalResultStatusEnums.SAFETY_EVALUATING.getCode().equals(resultDetail.getStatus())) {
            log.info("模型调用尚未完成，跳过安全评测，resultDetailId: {}, status: {}",
                    resultDetail.getId(), resultDetail.getStatus());
            return;
        }
        try {
            EvalResultDetailDO successResult = executeSafetyEvaluation(message, resultDetail);
            markResultSuccessAndIncreaseProgress(successResult, message.getTaskDetailId());
            log.info("安全评测消息处理完成，taskDetailId: {}, resultDetailId: {}",
                    message.getTaskDetailId(), message.getResultDetailId());
        } catch (Exception ex) {
            log.error("安全评测阶段执行失败，message: {}", message, ex);
            markResultFailedAndIncreaseProgress(resultDetail.getId(), message.getTaskDetailId(), ex);
        }
    }

    private EvalResultDetailDO executeSafetyEvaluation(SafetyEvaluateMessage message, EvalResultDetailDO resultDetail) {
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(message.getTaskDetailId());
        if (taskDetail == null) {
            throw new ServiceException("评测任务批次不存在，无法执行安全评测");
        }
        if (TaskStatusEnums.STOPPED.getCode().equals(taskDetail.getStatus())) {
            Long stoppedNodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                    PipelineNodeCodeEnums.L1, buildStoppedInputSnapshot("TASK_STOPPED_BEFORE_L1"));
            evalPipelineNodeRecorder.stopNode(stoppedNodeRecordId, "评测任务已停止，跳过 L1 判定",
                    buildStoppedNodeResult("TASK_STOPPED_BEFORE_L1"));
            return null;
        }
        // MODEL_CALLED -> SAFETY_EVALUATING 是安全评测阶段的幂等占位；重复消息再次进入时不会重复改写终态。
        markResultSafetyEvaluating(resultDetail.getId());
        String modelOutput = resultDetail.getModelOutput();
        if (modelOutput == null || modelOutput.isBlank()) {
            throw new ServiceException("模型输出为空，无法执行安全评测");
        }

        EvalResultDetailDO updateEntity = EvalResultDetailDO.builder()
                .id(resultDetail.getId())
                .modelOutput(resultDetail.getModelOutput())
                .rawResponse(resultDetail.getRawResponse())
                .latency(resultDetail.getLatency())
                .status(EvalResultStatusEnums.AUTO_SCORED.getCode())
                .build();
        L1JudgementResult l1JudgementResult = applyL1Judgement(taskDetail, resultDetail, updateEntity, modelOutput);
        if (isTaskStopped(message.getTaskDetailId())) {
            log.info("评测任务在安全评测期间被停止，丢弃本次安全评测结果，taskDetailId: {}, resultDetailId: {}",
                    message.getTaskDetailId(), resultDetail.getId());
            return null;
        }
        // L1 已经阻断时，样本具备明确违规结论，不再继续触发 L2/L3。
        if (!l1JudgementResult.blocked()) {
            applyL2Judgement(taskDetail, resultDetail, updateEntity, l1JudgementResult.warningTags(), modelOutput);
        }
        return updateEntity;
    }

    private void markResultSafetyEvaluating(Long resultDetailId) {
        transactionTemplate.executeWithoutResult(status -> {
            int updatedRows = evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, resultDetailId)
                    .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_CALLED.getCode())
                    .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.SAFETY_EVALUATING.getCode())
                    .set(EvalResultDetailDO::getErrorMsg, null));
            if (updatedRows > 0) {
                evalEsSyncOutboxService.recordResultDetailUpsert(resultDetailId);
            }
        });
    }

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
                log.info("L1 判定命中拦截，resultDetailId: {}, riskDetailsId: {}, keyword: {}",
                        resultDetail.getId(), context.getL1BlockDetailsId(), context.getL1BlockKeyword());
                return new L1JudgementResult(true, context.getHitWarningTags());
            }
            evalPipelineNodeRecorder.finishNode(l1NodeRecordId, PipelineNodeStatusEnums.PASSED,
                    buildL1OutputSnapshot(context), buildL1NodeResult(context, "PASSED"), null);
            log.info("L1 判定通过，resultDetailId: {}, warningTags: {}", resultDetail.getId(), context.getHitWarningTags());
            return new L1JudgementResult(false, context.getHitWarningTags());
        } catch (SecurityBlockException ex) {
            updateEntity.setIsSafe(false);
            updateEntity.setErrorMsg(buildL1BlockMessage(ex.getRiskDetailsId(), ex.getKeyword()));
            evalPipelineNodeRecorder.finishNode(l1NodeRecordId, PipelineNodeStatusEnums.BLOCKED,
                    null, buildL1BlockedExceptionNodeResult(ex), updateEntity.getErrorMsg());
            log.info("L1 判定抛出拦截异常，resultDetailId: {}, riskDetailsId: {}, keyword: {}",
                    resultDetail.getId(), ex.getRiskDetailsId(), ex.getKeyword());
            return new L1JudgementResult(true, List.of());
        } catch (Exception ex) {
            evalPipelineNodeRecorder.failNode(l1NodeRecordId, ex, null, buildFailedNodeResult("L1_FAILED"));
            throw ex;
        }
    }

    private void applyL2Judgement(EvalTaskDetailDO taskDetail,
                                  EvalResultDetailDO resultDetail,
                                  EvalResultDetailDO updateEntity,
                                  List<Long> l1WarningTags,
                                  String modelOutput) {
        Long l2NodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                PipelineNodeCodeEnums.L2, buildL2InputSnapshot(resultDetail, modelOutput, l1WarningTags));
        try {
            L2EvaluationResult l2Result = l2EvaluationService.evaluate(L2EvaluationRequest.builder()
                    .taskId(taskDetail.getTaskId())
                    .taskDetailId(taskDetail.getId())
                    .resultDetailId(resultDetail.getId())
                    .sampleId(resultDetail.getSampleId())
                    .targetRiskDetailsId(resultDetail.getRiskDetailsId())
                    .inputText(resultDetail.getInputText())
                    .modelOutput(modelOutput)
                    .l1WarningTags(l1WarningTags)
                    .build());
            updateEntity.setIsSafe(l2Result.getSafe());
            updateEntity.setErrorMsg(buildL2ResultMessage(l2Result));
            updateEntity.setStatus(EvalResultStatusEnums.AUTO_SCORED.getCode());
            PipelineNodeStatusEnums nodeStatus = l2Result.getSafe() != null && !l2Result.getSafe()
                    ? PipelineNodeStatusEnums.BLOCKED
                    : PipelineNodeStatusEnums.PASSED;
            evalPipelineNodeRecorder.finishNode(l2NodeRecordId, nodeStatus,
                    l2Result.getOutputSnapshot(), l2Result.getNodeResult(), null);
            if (l2Result.getDecisionType() == L2DecisionTypeEnums.PASS_TO_L3) {
                applyL3Judgement(taskDetail, resultDetail, updateEntity, l1WarningTags, modelOutput, l2Result);
            }
            log.info("L2 判定完成，resultDetailId: {}, decision: {}, safe: {}",
                    resultDetail.getId(), l2Result.getDecisionType(), l2Result.getSafe());
        } catch (Exception ex) {
            evalPipelineNodeRecorder.failNode(l2NodeRecordId, ex, null, buildFailedNodeResult("L2_FAILED"));
            throw ex;
        }
    }

    private void applyL3Judgement(EvalTaskDetailDO taskDetail,
                                  EvalResultDetailDO resultDetail,
                                  EvalResultDetailDO updateEntity,
                                  List<Long> l1WarningTags,
                                  String modelOutput,
                                  L2EvaluationResult l2Result) {
        Long l3NodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                PipelineNodeCodeEnums.L3, buildL3InputSnapshot(resultDetail, modelOutput, l1WarningTags, l2Result));
        try {
            L3EvaluationResult l3Result = l3EvaluationService.evaluate(L3EvaluationRequest.builder()
                    .taskId(taskDetail.getTaskId())
                    .taskDetailId(taskDetail.getId())
                    .resultDetailId(resultDetail.getId())
                    .sampleId(resultDetail.getSampleId())
                    .targetRiskDetailsId(resultDetail.getRiskDetailsId())
                    .inputText(resultDetail.getInputText())
                    .modelOutput(modelOutput)
                    .l1WarningTags(l1WarningTags)
                    .l2Decision(l2Result.getDecisionType())
                    .l2RouteReason(l2Result.getRouteReason())
                    .l2RiskDetailHits(l2Result.getRiskDetailHits())
                    .l2OutputSnapshot(l2Result.getOutputSnapshot())
                    .l2NodeResult(l2Result.getNodeResult())
                    .build());
            updateEntity.setIsSafe(l3Result.getSafe());
            updateEntity.setErrorMsg(buildL3ResultMessage(l3Result));
            updateEntity.setStatus(l3Result.getSafe() == null
                    ? EvalResultStatusEnums.MANUAL_REVIEWED.getCode()
                    : EvalResultStatusEnums.AUTO_SCORED.getCode());
            PipelineNodeStatusEnums nodeStatus = l3Result.getDecisionType() == L3DecisionTypeEnums.UNSAFE
                    ? PipelineNodeStatusEnums.BLOCKED
                    : PipelineNodeStatusEnums.PASSED;
            evalPipelineNodeRecorder.finishNode(l3NodeRecordId, nodeStatus,
                    l3Result.getOutputSnapshot(), l3Result.getNodeResult(), null);
            log.info("L3 判定完成，resultDetailId: {}, decision: {}, safe: {}, targetRiskDetailsId: {}",
                    resultDetail.getId(), l3Result.getDecisionType(), l3Result.getSafe(), l3Result.getTargetRiskDetailsId());
        } catch (Exception ex) {
            updateEntity.setIsSafe(null);
            updateEntity.setStatus(EvalResultStatusEnums.MANUAL_REVIEWED.getCode());
            updateEntity.setErrorMsg(trimErrorMessage("L3 执行异常，转入人工核验：" + ex.getMessage()));
            evalPipelineNodeRecorder.failNode(l3NodeRecordId, ex, null, buildL3FailedNodeResult(resultDetail, l2Result, ex));
            log.warn("L3 判定异常，样本转入人工核验，resultDetailId: {}, reason: {}", resultDetail.getId(), ex.getMessage(), ex);
        }
    }

    private void markResultSuccessAndIncreaseProgress(EvalResultDetailDO updateEntity, Long taskDetailId) {
        if (updateEntity == null) {
            return;
        }
        // 批次进度只在 L1/L2/L3 得到最终状态后推进，模型调用完成本身不计入完成数。
        transactionTemplate.executeWithoutResult(status -> {
            if (isTaskStopped(taskDetailId)) {
                return;
            }
            int updatedRows = updateResultSuccess(updateEntity);
            if (updatedRows > 0) {
                evalEsSyncOutboxService.recordResultDetailUpsert(updateEntity.getId());
                increaseTaskProgress(taskDetailId, false);
            }
        });
    }

    private void markResultFailedAndIncreaseProgress(Long resultDetailId, Long taskDetailId, Exception ex) {
        EvalResultDetailDO updateEntity = EvalResultDetailDO.builder()
                .id(resultDetailId)
                .status(EvalResultStatusEnums.FAILED.getCode())
                .isSafe(false)
                .errorMsg(trimErrorMessage(ex.getMessage()))
                .build();
        transactionTemplate.executeWithoutResult(status -> {
            if (isTaskStopped(taskDetailId)) {
                return;
            }
            int updatedRows = updateResultFailed(updateEntity);
            if (updatedRows > 0) {
                evalEsSyncOutboxService.recordResultDetailUpsert(resultDetailId);
                increaseTaskProgress(taskDetailId, true);
            }
        });
    }

    private int updateResultSuccess(EvalResultDetailDO updateEntity) {
        UpdateWrapper<EvalResultDetailDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", updateEntity.getId())
                .notIn("status", FINISHED_RESULT_STATUS)
                .set("model_output", updateEntity.getModelOutput())
                .set("raw_response", updateEntity.getRawResponse())
                .set("latency", updateEntity.getLatency())
                .set("is_safe", updateEntity.getIsSafe())
                .set("error_msg", updateEntity.getErrorMsg())
                .set("status", updateEntity.getStatus());
        return evalResultDetailMapper.update(null, updateWrapper);
    }

    private int updateResultFailed(EvalResultDetailDO updateEntity) {
        return evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getId, updateEntity.getId())
                .notIn(EvalResultDetailDO::getStatus, FINISHED_RESULT_STATUS)
                .set(EvalResultDetailDO::getIsSafe, updateEntity.getIsSafe())
                .set(EvalResultDetailDO::getErrorMsg, updateEntity.getErrorMsg())
                .set(EvalResultDetailDO::getStatus, updateEntity.getStatus()));
    }

    private void increaseTaskProgress(Long taskDetailId, boolean failed) {
        UpdateWrapper<EvalTaskDetailDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", taskDetailId)
                .setSql("finished_count = COALESCE(finished_count, 0) + 1");
        if (failed) {
            updateWrapper.setSql("failed_count = COALESCE(failed_count, 0) + 1");
        }
        evalTaskDetailMapper.update(null, updateWrapper);
        refreshTaskFinalStatus(taskDetailId);
    }

    private void refreshTaskFinalStatus(Long taskDetailId) {
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(taskDetailId);
        if (taskDetail == null || taskDetail.getTotalCount() == null || taskDetail.getFinishedCount() == null) {
            return;
        }
        if (taskDetail.getFinishedCount() < taskDetail.getTotalCount()) {
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
    }

    private boolean isTaskStopped(Long taskDetailId) {
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(taskDetailId);
        return taskDetail == null || TaskStatusEnums.STOPPED.getCode().equals(taskDetail.getStatus());
    }

    private RiskDetailRuleDO loadRiskDetailRuleSnapshot(Long targetRiskDetailsId) {
        if (targetRiskDetailsId == null) {
            return null;
        }
        return riskDetailRuleMapper.selectOne(new LambdaQueryWrapper<RiskDetailRuleDO>()
                .eq(RiskDetailRuleDO::getRiskDetailsId, targetRiskDetailsId)
                .eq(RiskDetailRuleDO::getStatus, 1)
                .last("LIMIT 1"));
    }

    private String buildL1BlockMessage(Long riskDetailsId, String keyword) {
        return "L1 安全拦截命中，riskDetailsId=" + riskDetailsId + ", keyword=" + keyword;
    }

    private String buildL2ResultMessage(L2EvaluationResult l2Result) {
        if (l2Result == null || l2Result.getDecisionType() == null) {
            return null;
        }
        if (l2Result.getDecisionType() == L2DecisionTypeEnums.SAFE
                || l2Result.getDecisionType() == L2DecisionTypeEnums.DEGRADED_SAFE) {
            return null;
        }
        return trimErrorMessage("L2 安全判定：" + l2Result.getDecisionType().getDesc()
                + ", riskDetailsId=" + l2Result.getRiskDetailsId()
                + ", reason=" + l2Result.getRouteReason());
    }

    private String buildL3ResultMessage(L3EvaluationResult l3Result) {
        if (l3Result == null || l3Result.getDecisionType() == null) {
            return null;
        }
        if (l3Result.getDecisionType() == L3DecisionTypeEnums.SAFE) {
            return null;
        }
        return trimErrorMessage("L3 安全判定：" + l3Result.getDecisionType().getDesc()
                + ", targetRiskDetailsId=" + l3Result.getTargetRiskDetailsId()
                + ", riskDetailsId=" + l3Result.getRiskDetailsId()
                + ", reason=" + l3Result.getRouteReason());
    }

    private Object buildL1InputSnapshot(String modelOutput) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelOutput", modelOutput);
        return snapshot;
    }

    private Object buildL1OutputSnapshot(EvalContext context) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("l1Blocked", context.isL1Blocked());
        snapshot.put("l1BlockDetailsId", context.getL1BlockDetailsId());
        snapshot.put("l1BlockKeyword", context.getL1BlockKeyword());
        snapshot.put("hitWarningTags", context.getHitWarningTags());
        return snapshot;
    }

    private Object buildL1NodeResult(EvalContext context, String decision) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("blocked", context.isL1Blocked());
        nodeResult.put("riskDetailsId", context.getL1BlockDetailsId());
        nodeResult.put("keyword", context.getL1BlockKeyword());
        nodeResult.put("warningTags", context.getHitWarningTags());
        nodeResult.put("decision", decision);
        return nodeResult;
    }

    private Object buildL1BlockedExceptionNodeResult(SecurityBlockException ex) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("blocked", true);
        nodeResult.put("riskDetailsId", ex.getRiskDetailsId());
        nodeResult.put("keyword", ex.getKeyword());
        nodeResult.put("decision", "BLOCKED");
        return nodeResult;
    }

    private Object buildL2InputSnapshot(EvalResultDetailDO resultDetail, String modelOutput, List<Long> l1WarningTags) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("inputText", resultDetail.getInputText());
        snapshot.put("targetRiskDetailsId", resultDetail.getRiskDetailsId());
        snapshot.put("modelOutput", modelOutput);
        snapshot.put("l1WarningTags", l1WarningTags);
        return snapshot;
    }

    private Object buildL3InputSnapshot(EvalResultDetailDO resultDetail,
                                        String modelOutput,
                                        List<Long> l1WarningTags,
                                        L2EvaluationResult l2Result) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("inputText", resultDetail.getInputText());
        snapshot.put("modelOutput", modelOutput);
        snapshot.put("targetRiskDetailsId", resultDetail.getRiskDetailsId());
        snapshot.put("riskDetailRule", loadRiskDetailRuleSnapshot(resultDetail.getRiskDetailsId()));
        snapshot.put("l1WarningTags", l1WarningTags);
        snapshot.put("l2Decision", l2Result == null || l2Result.getDecisionType() == null
                ? null : l2Result.getDecisionType().getCode());
        snapshot.put("l2RouteReason", l2Result == null ? null : l2Result.getRouteReason());
        snapshot.put("l2RiskDetailHits", l2Result == null ? null : l2Result.getRiskDetailHits());
        snapshot.put("l2OutputSnapshot", l2Result == null ? null : l2Result.getOutputSnapshot());
        snapshot.put("l2NodeResult", l2Result == null ? null : l2Result.getNodeResult());
        return snapshot;
    }

    private Object buildFailedNodeResult(String decision) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("success", false);
        nodeResult.put("decision", decision);
        return nodeResult;
    }

    private Object buildL3FailedNodeResult(EvalResultDetailDO resultDetail, L2EvaluationResult l2Result, Exception ex) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("success", false);
        nodeResult.put("decision", "L3_FAILED_MANUAL_REVIEW");
        nodeResult.put("safe", null);
        nodeResult.put("score", null);
        nodeResult.put("targetRiskDetailsId", resultDetail == null ? null : resultDetail.getRiskDetailsId());
        nodeResult.put("riskDetailsId", l2Result == null ? null : l2Result.getRiskDetailsId());
        nodeResult.put("routeReason", "L3 执行异常，样本转入人工核验");
        nodeResult.put("error", ex == null ? null : ex.getMessage());
        return nodeResult;
    }

    private Object buildStoppedInputSnapshot(String reason) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("reason", reason);
        return snapshot;
    }

    private Object buildStoppedNodeResult(String decision) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("stopped", true);
        nodeResult.put("decision", decision);
        return nodeResult;
    }

    private String trimErrorMessage(String message) {
        if (message == null) {
            return "安全评测阶段执行失败";
        }
        int maxLength = 1000;
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }

    private record L1JudgementResult(boolean blocked, List<Long> warningTags) {
    }
}
