package com.kant.llm.eval.pipeline.mq.consumer;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kant.ai.infra.client.ClientModelInfo;
import com.kant.ai.infra.client.ModelClientStrategy;
import com.kant.ai.infra.client.ModelClientStrategyFactory;
import com.kant.ai.infra.client.ModelRequest;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.framework.errorcode.BaseErrorCode;
import com.kant.framework.exception.ServiceException;
import com.kant.framework.mq.MessageWrapper;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.PipelineNodeCodeEnums;
import com.kant.llm.common.enums.PipelineNodeStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.basesource.dao.entity.MockModelOutputContentDO;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.dao.mapper.ModelInfoMapper;
import com.kant.llm.eval.basesource.dto.resp.ModelInfoVO;
import com.kant.llm.eval.basesource.service.MockModelOutputContentService;
import com.kant.llm.eval.es.service.EvalEsSyncOutboxService;
import com.kant.llm.eval.pipeline.mq.message.ModelCallMessage;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import com.kant.llm.eval.pipeline.service.EvalPipelineNodeRecorder;
import com.kant.llm.eval.pipeline.service.ModelCallExecutionService;
import com.kant.llm.eval.pipeline.service.ratelimit.ModelCallPermit;
import com.kant.llm.eval.pipeline.service.ratelimit.ModelCallRateLimitedException;
import com.kant.llm.eval.pipeline.service.ratelimit.ModelCallRateLimiter;
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
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 单样本模型调用消费者。
 *
 * <p>该消费者只负责“调用被评测模型”这一段异步阶段：先根据 resultDetailId 加分布式执行锁，
 * 再按 model_info.max_thread_size 获取 Redisson 分布式信号量许可，最后调用模型并把输出落库。
 * 模型输出写入 eval_result_detail 后，会投递 SafetyEvaluate_MQ，由后续消费者继续执行 L1/L2/L3。</p>
 */
@Slf4j
@Component
//@RocketMQMessageListener(
//        topic = EvalMqTopics.MODEL_CALL_TOPIC,
//        consumerGroup = "aigc-eval-model-call-consumer-group"
//)
public class ModelCallConsumer implements RocketMQListener<MessageWrapper<ModelCallMessage>>, ModelCallExecutionService {

    private static final String EXECUTION_LOCK_KEY_PREFIX = "aigc-eval:eval-result:execute:";
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
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final EvalPipelineNodeRecorder evalPipelineNodeRecorder;
    private final MockModelOutputContentService mockModelOutputContentService;
    private final MessageQueueProducer messageQueueProducer;
    private final ModelCallRateLimiter modelCallRateLimiter;
    private final EvalEsSyncOutboxService evalEsSyncOutboxService;

    public ModelCallConsumer(EvalResultDetailMapper evalResultDetailMapper,
                             EvalTaskDetailMapper evalTaskDetailMapper,
                             ModelInfoMapper modelInfoMapper,
                             ModelClientStrategyFactory modelClientStrategyFactory,
                             RedissonClient redissonClient,
                             TransactionTemplate transactionTemplate,
                             EvalPipelineNodeRecorder evalPipelineNodeRecorder,
                             MockModelOutputContentService mockModelOutputContentService,
                             MessageQueueProducer messageQueueProducer,
                             ModelCallRateLimiter modelCallRateLimiter,
                             EvalEsSyncOutboxService evalEsSyncOutboxService) {
        this.evalResultDetailMapper = evalResultDetailMapper;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.modelClientStrategyFactory = modelClientStrategyFactory;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.evalPipelineNodeRecorder = evalPipelineNodeRecorder;
        this.mockModelOutputContentService = mockModelOutputContentService;
        this.messageQueueProducer = messageQueueProducer;
        this.modelCallRateLimiter = modelCallRateLimiter;
        this.evalEsSyncOutboxService = evalEsSyncOutboxService;
    }

    @Override
    public void onMessage(MessageWrapper<ModelCallMessage> messageWrapper) {
        ModelCallMessage message = messageWrapper.getBody();
        log.info("开始消费模型调用消息，topic: {}, taskDetailId: {}, resultDetailId: {}, sampleId: {}, modelId: {}",
                EvalMqTopics.MODEL_CALL_TOPIC, message.getTaskDetailId(), message.getResultDetailId(),
                message.getSampleId(), message.getModelId());
        RLock lock = redissonClient.getLock(EXECUTION_LOCK_KEY_PREFIX + message.getResultDetailId());
        boolean locked;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("模型调用消费者获取执行锁被中断", ex, BaseErrorCode.SERVICE_ERROR);
        }
        if (!locked) {
            log.info("评测结果明细正在执行中，跳过重复模型调用消息，resultDetailId: {}",
                    message.getResultDetailId());
            return;
        }
        try {
            // 执行
            execute(message);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void execute(ModelCallMessage message) {
        EvalResultDetailDO resultDetail = evalResultDetailMapper.selectById(message.getResultDetailId());
        if (resultDetail == null) {
            log.warn("评测结果明细不存在，跳过模型调用，message: {}", message);
            return;
        }
        if (FINISHED_RESULT_STATUS.contains(resultDetail.getStatus())) {
            log.info("评测结果明细已处于终态，跳过模型调用，resultDetailId: {}, status: {}",
                    resultDetail.getId(), resultDetail.getStatus());
            return;
        }
        // 模型输出已经落库但安全评测消息可能丢失时，只补投下游消息，绝不能再次调用模型。
        if (EvalResultStatusEnums.MODEL_CALLED.getCode().equals(resultDetail.getStatus())) {
            log.info("评测结果明细已完成模型调用，补投安全评测消息，resultDetailId: {}", resultDetail.getId());
            sendSafetyEvaluateMessage(message);
            return;
        }
        if (!EvalResultStatusEnums.PENDING.getCode().equals(resultDetail.getStatus())) {
            log.info("评测结果明细不是待处理状态，跳过模型调用，resultDetailId: {}, status: {}",
                    resultDetail.getId(), resultDetail.getStatus());
            return;
        }

        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(message.getTaskDetailId());
        if (taskDetail == null) {
            markResultFailedAndIncreaseProgress(resultDetail.getId(), message.getTaskDetailId(),
                    new ServiceException("评测任务批次不存在，无法调用被评测模型"));
            return;
        }
        if (TaskStatusEnums.STOPPED.getCode().equals(taskDetail.getStatus())) {
            recordStoppedModelCall(taskDetail, resultDetail, "TASK_STOPPED_BEFORE_MODEL_CALL");
            markResultStopped(resultDetail.getId(), "评测任务已停止，样本未进入模型调用");
            return;
        }

        boolean modelCalled = false;
        ModelCallPermit permit = null;
        try {
            ModelInfoDO modelInfoDO = loadModelInfoDO(message.getModelId());
            // 先获取分布式并发许可，再把样本置为 MODEL_RUNNING；拿不到许可时保持 PENDING，等待 MQ 重试。
            permit = modelCallRateLimiter.acquire(BeanUtil.copyProperties(modelInfoDO, ModelInfoVO.class));
            int runningRows = markResultModelRunning(resultDetail.getId());
            if (runningRows <= 0) {
                EvalResultDetailDO latest = evalResultDetailMapper.selectById(resultDetail.getId());
                if (latest != null && EvalResultStatusEnums.MODEL_CALLED.getCode().equals(latest.getStatus())) {
                    modelCalled = true;
                } else {
                    return;
                }
            } else {
                markTaskRunning(taskDetail);
                ClientModelInfo modelInfo = buildClientModelInfo(modelInfoDO);
                ModelResponse modelResponse = callModel(taskDetail, resultDetail, modelInfo);
                if (isTaskStopped(message.getTaskDetailId())) {
                    markResultStopped(resultDetail.getId(), "评测任务已停止，模型调用完成后不再进入安全评测");
                    return;
                }
                // 模型输出只在 MODEL_RUNNING -> MODEL_CALLED 的条件更新中落库，避免重复消息覆盖终态结果。
                int updatedRows = markResultModelCalled(resultDetail.getId(), modelResponse);
                modelCalled = updatedRows > 0;
                log.info("模型调用阶段完成，taskDetailId: {}, resultDetailId: {}, modelId: {}, elapsed: {}, updated: {}",
                        taskDetail.getId(), resultDetail.getId(), message.getModelId(), modelResponse.getElapsed(), modelCalled);
            }
        } catch (ModelCallRateLimitedException ex) {
            log.info("模型调用触发并发限流，交给 MQ 后续重试，resultDetailId: {}, reason: {}",
                    resultDetail.getId(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("模型调用阶段执行失败，resultDetailId: {}, message: {}", resultDetail.getId(), message, ex);
            markResultFailedAndIncreaseProgress(resultDetail.getId(), message.getTaskDetailId(), ex);
            return;
        } finally {
            if (permit != null) {
                permit.release();
            }
        }

        if (modelCalled) {
            // 安全评测作为下一段 MQ 链路异步接力；若发送失败，当前模型调用消息会重试并补投。
            sendSafetyEvaluateMessage(message);
        }
    }

    private ModelResponse callModel(EvalTaskDetailDO taskDetail, EvalResultDetailDO resultDetail, ClientModelInfo modelInfo) {
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(modelInfo);
        Long modelCallNodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                PipelineNodeCodeEnums.MODEL_CALL, buildModelCallInputSnapshot(modelInfo, resultDetail));
        try {
            ModelResponse modelResponse = strategy.call(ModelRequest.builder()
                    .clientModelInfo(modelInfo)
                    .inputText(resultDetail.getInputText())
                    .mockModelOutput(getMockOutput(modelInfo.getModelId(), resultDetail.getSampleId()))
                    .build());
            evalPipelineNodeRecorder.finishNode(modelCallNodeRecordId, PipelineNodeStatusEnums.PASSED,
                    buildModelCallOutputSnapshot(modelResponse), buildModelCallNodeResult(modelResponse), null);
            return modelResponse;
        } catch (Exception ex) {
            evalPipelineNodeRecorder.failNode(modelCallNodeRecordId, ex, null, buildFailedNodeResult("MODEL_CALL_FAILED"));
            throw ex;
        }
    }

    private ModelInfoDO loadModelInfoDO(Long modelId) {
        ModelInfoDO modelInfoDO = modelInfoMapper.selectById(modelId);
        if (modelInfoDO == null) {
            throw new ServiceException("模型配置不存在，无法执行模型调用");
        }
        return modelInfoDO;
    }

    private ClientModelInfo buildClientModelInfo(ModelInfoDO modelInfoDO) {
        ModelManufacturerEnum manufacturerType;
        try {
            manufacturerType = ModelManufacturerEnum.valueOf(modelInfoDO.getManufacturerCode().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ServiceException("不支持的大模型厂商标识：" + modelInfoDO.getManufacturerCode(),
                    ex, BaseErrorCode.SERVICE_ERROR);
        }
        return ClientModelInfo.builder()
                .modelId(modelInfoDO.getId())
                .model(modelInfoDO.getModel())
                .apiKey(modelInfoDO.getApiKey())
                .baseUrl(modelInfoDO.getBaseUrl())
                .manufacturerType(manufacturerType)
                .build();
    }

    private String getMockOutput(Long modelId, Long sampleId) {
        List<MockModelOutputContentDO> records = mockModelOutputContentService.list(new LambdaQueryWrapper<MockModelOutputContentDO>()
                .eq(MockModelOutputContentDO::getBindModelId, modelId)
                .eq(MockModelOutputContentDO::getBindSampleId, sampleId)
                .eq(MockModelOutputContentDO::getDeleted, false));
        return records == null || records.isEmpty() ? null : records.get(0).getOutputText();
    }

    private int markResultModelRunning(Long resultDetailId) {
        return transactionTemplate.execute(status -> {
            int updatedRows = evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, resultDetailId)
                    .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.PENDING.getCode())
                    .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_RUNNING.getCode())
                    .set(EvalResultDetailDO::getErrorMsg, null));
            if (updatedRows > 0) {
                evalEsSyncOutboxService.recordResultDetailUpsert(resultDetailId);
            }
            return updatedRows;
        });
    }

    private int markResultModelCalled(Long resultDetailId, ModelResponse modelResponse) {
        UpdateWrapper<EvalResultDetailDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", resultDetailId)
                .eq("status", EvalResultStatusEnums.MODEL_RUNNING.getCode())
                .set("model_output", modelResponse.getRespContent())
                .set("raw_response", JSON.toJSONString(modelResponse))
                .set("latency", toIntegerLatency(modelResponse.getElapsed()))
                .set("error_msg", null)
                .set("status", EvalResultStatusEnums.MODEL_CALLED.getCode());
        return transactionTemplate.execute(status -> {
            int updatedRows = evalResultDetailMapper.update(null, updateWrapper);
            if (updatedRows > 0) {
                evalEsSyncOutboxService.recordResultDetailUpsert(resultDetailId);
            }
            return updatedRows;
        });
    }

    private void markTaskRunning(EvalTaskDetailDO taskDetail) {
        evalTaskDetailMapper.update(null, new LambdaUpdateWrapper<EvalTaskDetailDO>()
                .eq(EvalTaskDetailDO::getId, taskDetail.getId())
                .eq(EvalTaskDetailDO::getStatus, TaskStatusEnums.READY.getCode())
                .set(EvalTaskDetailDO::getStatus, TaskStatusEnums.RUNNING.getCode())
                .set(EvalTaskDetailDO::getStartTime, taskDetail.getStartTime() == null ? LocalDateTime.now() : taskDetail.getStartTime()));
    }

    private void sendSafetyEvaluateMessage(ModelCallMessage message) {
        messageQueueProducer.send(MessageSendRequest.builder()
                .topic(EvalMqTopics.SAFETY_EVALUATE_TOPIC)
                .keys(message.getResultDetailId().toString())
                .bizDesc("单样本安全评测消息")
                .body(SafetyEvaluateMessage.builder()
                        .taskDetailId(message.getTaskDetailId())
                        .resultDetailId(message.getResultDetailId())
                        .taskId(message.getTaskId())
                        .sampleId(message.getSampleId())
                        .modelId(message.getModelId())
                        .build())
                .build());
    }

    private void recordStoppedModelCall(EvalTaskDetailDO taskDetail, EvalResultDetailDO resultDetail, String reason) {
        Long stoppedNodeRecordId = evalPipelineNodeRecorder.startNode(taskDetail, resultDetail,
                PipelineNodeCodeEnums.MODEL_CALL, buildStoppedInputSnapshot(reason));
        evalPipelineNodeRecorder.stopNode(stoppedNodeRecordId, "评测任务已停止，跳过模型调用",
                buildStoppedNodeResult(reason));
    }

    private void markResultStopped(Long resultDetailId, String errorMsg) {
        transactionTemplate.executeWithoutResult(status -> {
            int updatedRows = evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, resultDetailId)
                    .notIn(EvalResultDetailDO::getStatus, FINISHED_RESULT_STATUS)
                    .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.STOPPED.getCode())
                    .set(EvalResultDetailDO::getErrorMsg, trimErrorMessage(errorMsg)));
            if (updatedRows > 0) {
                evalEsSyncOutboxService.recordResultDetailUpsert(resultDetailId);
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
            int updatedRows = evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, updateEntity.getId())
                    .notIn(EvalResultDetailDO::getStatus, FINISHED_RESULT_STATUS)
                    .set(EvalResultDetailDO::getIsSafe, updateEntity.getIsSafe())
                    .set(EvalResultDetailDO::getErrorMsg, updateEntity.getErrorMsg())
                    .set(EvalResultDetailDO::getStatus, updateEntity.getStatus()));
            if (updatedRows > 0) {
                evalEsSyncOutboxService.recordResultDetailUpsert(resultDetailId);
                increaseTaskProgress(taskDetailId, true);
            }
        });
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

    private Integer toIntegerLatency(Long elapsed) {
        if (elapsed == null) {
            return null;
        }
        return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : elapsed.intValue();
    }

    private Object buildModelCallInputSnapshot(ClientModelInfo modelInfo, EvalResultDetailDO resultDetail) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelId", modelInfo.getModelId());
        snapshot.put("manufacturerType", modelInfo.getManufacturerType());
        snapshot.put("model", modelInfo.getModel());
        snapshot.put("inputText", resultDetail.getInputText());
        return snapshot;
    }

    private Object buildModelCallOutputSnapshot(ModelResponse modelResponse) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelId", modelResponse.getModelId());
        snapshot.put("respContent", modelResponse.getRespContent());
        snapshot.put("elapsed", modelResponse.getElapsed());
        return snapshot;
    }

    private Object buildModelCallNodeResult(ModelResponse modelResponse) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("success", true);
        nodeResult.put("elapsed", modelResponse.getElapsed());
        return nodeResult;
    }

    private Object buildFailedNodeResult(String decision) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("success", false);
        nodeResult.put("decision", decision);
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
            return "模型调用阶段执行失败";
        }
        int maxLength = 1000;
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
