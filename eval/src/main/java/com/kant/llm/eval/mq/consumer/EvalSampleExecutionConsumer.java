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
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 单样本执行消费者。
 *
 * <p>该消费者承接任务拆分后的 Execution_MQ 消息，负责完成“调用被测模型 -> L1 安全判定
 * -> 回写单条结果 -> 推进批次进度”的第一版执行闭环。L2/L3 尚未接入时，未被 L1 一级风险词拦截
 * 的输出先按自动评分安全处理。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.EXECUTION,
        consumerGroup = "aigc-eval-sample-execution-consumer-group"
)
public class EvalSampleExecutionConsumer implements RocketMQListener<EvalSampleExecutionMessage> {

    private static final String EXECUTION_LOCK_KEY_PREFIX = "aigc-eval:eval-result:execute:";

    private static final BigDecimal SAFE_SCORE = BigDecimal.ONE;

    private static final BigDecimal BLOCK_SCORE = BigDecimal.ZERO;

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

    public EvalSampleExecutionConsumer(EvalResultDetailMapper evalResultDetailMapper,
                                       EvalTaskDetailMapper evalTaskDetailMapper,
                                       ModelInfoMapper modelInfoMapper,
                                       ModelClientStrategyFactory modelClientStrategyFactory,
                                       L1InterceptionEngine l1InterceptionEngine,
                                       RedissonClient redissonClient,
                                       TransactionTemplate transactionTemplate) {
        this.evalResultDetailMapper = evalResultDetailMapper;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.modelClientStrategyFactory = modelClientStrategyFactory;
        this.l1InterceptionEngine = l1InterceptionEngine;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
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
            return null;
        }
        markTaskRunning(taskDetail);
        log.info("评测样本开始调用被测模型，taskDetailId: {}, resultDetailId: {}, modelId: {}",
                taskDetail.getId(), resultDetail.getId(), message.getModelId());

        ModelInfo modelInfo = loadModelInfo(message.getModelId());
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(modelInfo);
        // 开始调用大模型
        ModelResponse modelResponse = strategy.call(ModelRequest.builder()
                .modelInfo(modelInfo)
                .inputText(resultDetail.getInputText())
                .build());
        log.info("评测样本模型调用完成，taskDetailId: {}, resultDetailId: {}, modelId: {}, elapsed: {}",
                taskDetail.getId(), resultDetail.getId(), message.getModelId(), modelResponse.getElapsed());

        EvalResultDetailDO updateEntity = EvalResultDetailDO.builder()
                .id(resultDetail.getId())
                .modelOutput(modelResponse.getRespContent())
                .rawResponse(JSON.toJSONString(modelResponse))
                .latency(toIntegerLatency(modelResponse.getElapsed()))
                .status(EvalResultStatusEnums.AUTO_SCORED.getCode())
                .build();
        // L1层拦截
        applyL1Judgement(updateEntity, modelResponse.getRespContent());
        // 模型调用无法强制中断，返回后再次确认批次状态；若用户已停止，丢弃本次结果。
        if (isTaskStopped(message.getTaskDetailId())) {
            log.info("评测任务批次已在模型调用期间被终止，丢弃模型返回结果，taskDetailId: {}, resultDetailId: {}",
                    message.getTaskDetailId(), resultDetail.getId());
            return null;
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
     * 当前阶段尚未接入 L2/L3，未命中一级风险词的输出先进入安全闭环。</p>
     */
    private void applyL1Judgement(EvalResultDetailDO updateEntity, String modelOutput) {
        try {
            EvalContext context = l1InterceptionEngine.analyze(modelOutput);
            updateEntity.setIsSafe(!context.isL1Blocked());
            updateEntity.setScore(context.isL1Blocked() ? BLOCK_SCORE : SAFE_SCORE);
            if (context.isL1Blocked()) {
                updateEntity.setErrorMsg(buildL1BlockMessage(context.getL1BlockDetailsId(), context.getL1BlockKeyword()));
                log.info("评测样本 L1 判定命中拦截，resultDetailId: {}, riskDetailsId: {}, keyword: {}",
                        updateEntity.getId(), context.getL1BlockDetailsId(), context.getL1BlockKeyword());
            } else {
                log.info("评测样本 L1 判定通过，resultDetailId: {}", updateEntity.getId());
            }
        } catch (SecurityBlockException ex) {
            updateEntity.setIsSafe(false);
            updateEntity.setScore(BLOCK_SCORE);
            updateEntity.setErrorMsg(buildL1BlockMessage(ex.getRiskDetailsId(), ex.getKeyword()));
            log.info("评测样本 L1 判定抛出拦截异常，resultDetailId: {}, riskDetailsId: {}, keyword: {}",
                    updateEntity.getId(), ex.getRiskDetailsId(), ex.getKeyword());
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
                .score(BLOCK_SCORE)
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
        return evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getId, updateEntity.getId())
                .notIn(EvalResultDetailDO::getStatus, FINISHED_RESULT_STATUS)
                .set(EvalResultDetailDO::getModelOutput, updateEntity.getModelOutput())
                .set(EvalResultDetailDO::getRawResponse, updateEntity.getRawResponse())
                .set(EvalResultDetailDO::getLatency, updateEntity.getLatency())
                .set(EvalResultDetailDO::getIsSafe, updateEntity.getIsSafe())
                .set(EvalResultDetailDO::getScore, updateEntity.getScore())
                .set(EvalResultDetailDO::getErrorMsg, updateEntity.getErrorMsg())
                .set(EvalResultDetailDO::getStatus, updateEntity.getStatus()));
    }

    /**
     * 条件更新失败结果，避免重复消息把同一条结果重复计入失败。
     */
    private int updateResultFailed(EvalResultDetailDO updateEntity) {
        return evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getId, updateEntity.getId())
                .notIn(EvalResultDetailDO::getStatus, FINISHED_RESULT_STATUS)
                .set(EvalResultDetailDO::getIsSafe, updateEntity.getIsSafe())
                .set(EvalResultDetailDO::getScore, updateEntity.getScore())
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
     * 错误信息落库时做长度保护，避免第三方异常堆叠过长导致更新失败。
     */
    private String trimErrorMessage(String message) {
        if (message == null) {
            return "评测样本执行失败";
        }
        int maxLength = 1000;
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
