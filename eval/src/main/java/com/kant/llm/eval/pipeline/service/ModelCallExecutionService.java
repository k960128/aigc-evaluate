package com.kant.llm.eval.pipeline.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.ai.infra.client.*;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.framework.exception.ServiceException;
import com.kant.framework.errorcode.BaseErrorCode;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.PipelineNodeCodeEnums;
import com.kant.llm.common.enums.PipelineNodeStatusEnums;
import com.kant.llm.eval.basesource.dao.entity.MockModelOutputContentDO;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.dto.resp.ModelInfoVO;
import com.kant.llm.eval.basesource.service.MockModelOutputContentService;
import com.kant.llm.eval.basesource.service.ModelInfoService;
import com.kant.llm.eval.pipeline.record.ModelCallMessage;
import com.kant.llm.eval.pipeline.record.SafetyEvaluateMessage;
import com.kant.llm.eval.pipeline.record.EvalPipelineNodeRecorder;
import com.kant.llm.eval.pipeline.ratelimit.ModelCallPermit;
import com.kant.llm.eval.pipeline.ratelimit.ModelCallRateLimiter;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ModelCallExecutionService {

    private final RedissonClient redissonClient;
    private final ModelInfoService modelInfoService;
    private final MessageQueueProducer messageQueueProducer;
    private final ModelCallRateLimiter modelCallRateLimiter;
    private final SafetyEvaluateResultWriter resultWriter;
    private final EvalPipelineNodeRecorder evalPipelineNodeRecorder;
    private final ModelClientStrategyFactory modelClientStrategyFactory;
    private final MockModelOutputContentService mockModelOutputContentService;

    public ModelCallExecutionService(RedissonClient redissonClient,
                                     ModelInfoService modelInfoService,
                                     MessageQueueProducer messageQueueProducer,
                                     ModelCallRateLimiter modelCallRateLimiter,
                                     SafetyEvaluateResultWriter resultWriter,
                                     EvalPipelineNodeRecorder evalPipelineNodeRecorder,
                                     ModelClientStrategyFactory modelClientStrategyFactory,
                                     MockModelOutputContentService mockModelOutputContentService) {
        this.redissonClient = redissonClient;
        this.modelInfoService = modelInfoService;
        this.messageQueueProducer = messageQueueProducer;
        this.modelCallRateLimiter = modelCallRateLimiter;
        this.resultWriter = resultWriter;
        this.evalPipelineNodeRecorder = evalPipelineNodeRecorder;
        this.modelClientStrategyFactory = modelClientStrategyFactory;
        this.mockModelOutputContentService = mockModelOutputContentService;
    }


    /**
     *  提交执行模型调用
     * @param message msg
     */
    public void submitExecute(ModelCallMessage message) {
        if (message == null || message.getResultDetailId() == null) {
            throw new ServiceException("模型调用消息缺少结果明细ID");
        }
        String lockKey = ResultExecution.lockKey(message.getResultDetailId());
        RLock rLock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = rLock.tryLock(10, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("样本正在执行，请稍后重试，resultDetailId: " + message.getResultDetailId());
            }
            execute(message, rLock);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("模型调用获取锁被中断", e, BaseErrorCode.SERVICE_ERROR);
        } catch (RuntimeException e) {
            if (locked) {
                try {
                    resultWriter.recordFailure(message.getResultDetailId(), message.getExecutionVersion(), e);
                } catch (RuntimeException recordException) {
                    e.addSuppressed(recordException);
                }
            }
            throw new ServiceException("模型调用处理失败，resultDetailId: " + message.getResultDetailId(),
                    e, BaseErrorCode.SERVICE_ERROR);
        } finally {
            if (locked) {
                try {
                    if (rLock.isHeldByCurrentThread()) {
                        rLock.unlock();
                    }
                } catch (RuntimeException e) {
                    log.warn("模型调用释放锁失败，lockKey: {}", lockKey, e);
                }
            }
        }
    }

    /**
     *  执行模型调用
     * @param message msg
     */
    private void execute(ModelCallMessage message, RLock lock) {
        EvalResultDetailDO resultDetail = resultWriter.claimModel(message.getResultDetailId(),
                ResultExecution.version(message.getExecutionVersion()));
        if (resultDetail == null) {
            return;
        }
        if (EvalResultStatusEnums.MODEL_CALLED.getCode().equals(resultDetail.getStatus())) {
            sendSafetyEvaluateMessage(message);
            return;
        }
        ModelInfoVO modelInfo = loadModelInfo(message.getModelId());
        ModelCallPermit acquire = null;
        try {
            // 获取分布式模型调用许可
            acquire = modelCallRateLimiter.acquire(modelInfo);
            // 执行模型调用逻辑
            ModelResponse modelResponse = modelCall(modelInfo, resultDetail);
            if (modelResponse == null || modelResponse.getRespContent() == null || modelResponse.getRespContent().isBlank()) {
                throw new ServiceException("被评测模型未返回有效输出");
            }
            if (!lock.isHeldByCurrentThread()) {
                throw new ServiceException("模型调用执行锁已丢失");
            }
            if (resultWriter.completeModel(resultDetail, modelResponse)) {
                // 模型正常调用成功，发送安全评测消息
                sendSafetyEvaluateMessage(message);
            }
        } finally {
            if (acquire != null) {
                acquire.release();
            }
        }

    }

    /**
     * 发送安全评测消息
     * @param message msg
     */
    private void sendSafetyEvaluateMessage(ModelCallMessage message) {
        messageQueueProducer.send(MessageSendRequest.builder()
                .topic(EvalMqTopics.SAFETY_EVALUATE_TOPIC)
                .keys(message.getResultDetailId().toString())
                .bizDesc("单样本安全评测消息")
                .body(SafetyEvaluateMessage.builder()
                        .taskDetailId(message.getTaskDetailId())
                        .resultDetailId(message.getResultDetailId())
                        .executionVersion(message.getExecutionVersion())
                        .taskId(message.getTaskId())
                        .sampleId(message.getSampleId())
                        .modelId(message.getModelId())
                        .build())
                .build());
    }

    /**
     * 执行模型调用
     * @param modelInfo modelInfo
     * @param resultDetail resultDetail
     * @return ModelResponse
     */
    private ModelResponse modelCall(ModelInfoVO modelInfo, EvalResultDetailDO resultDetail) {
        // 1. 构建大模型客户端信息
        ClientModelInfo clientModelInfo = ClientModelInfo.builder()
                .modelId(modelInfo.getId())
                .model(modelInfo.getModel())
                .apiKey(modelInfo.getApiKey())
                .baseUrl(modelInfo.getBaseUrl())
                .manufacturerType(ModelManufacturerEnum
                        .valueOf(modelInfo.getManufacturerCode().toUpperCase(Locale.ROOT)))
                .build();
        // 2. 记录modelCall节点日志
        Long modelCallNodeRecordId = evalPipelineNodeRecorder.startNode(
                resultDetail.getTaskId(),
                resultDetail.getTaskDetailId(),
                resultDetail,
                PipelineNodeCodeEnums.MODEL_CALL,
                buildModelCallInputSnapshot(clientModelInfo, resultDetail));
        try {
            // 3. 获取模型调用策略
            ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(clientModelInfo);
            // 4. 调用模型策略
            ModelResponse modelResponse = strategy.call(ModelRequest.builder()
                    .clientModelInfo(clientModelInfo)
                    .inputText(resultDetail.getInputText())
                    .mockModelOutput(mockModelOutput(clientModelInfo.getModelId(), resultDetail.getSampleId()))
                    .build());
            // 5. 记录modelCall节点处理完成日志
            evalPipelineNodeRecorder.finishNode
                    (modelCallNodeRecordId,
                            PipelineNodeStatusEnums.PASSED,
                            buildModelCallOutputSnapshot(modelResponse),
                            buildModelCallNodeResult(modelResponse),
                            null);

            return modelResponse;
        } catch (Exception e) {
            // 6. 记录modelCall节点处理失败日志
            evalPipelineNodeRecorder.failNode(
                    modelCallNodeRecordId,
                    e,
                    null,
                    buildFailedNodeResult("MODEL_CALL_FAILED"));
            throw e;
        }
    }


    /**
     获取模型信息
     * @param modelId 模型ID
     * @return 组装后的模型信息
     */
    private ModelInfoVO loadModelInfo(Long modelId) {
        ModelInfoDO modelInfoDO = modelInfoService.getById(modelId);
        if (ObjectUtil.isNotNull(modelInfoDO)) {
            return BeanUtil.copyProperties(modelInfoDO, ModelInfoVO.class);
        }
        throw new ServiceException("模型配置不存在，无法执行模型调用");
    }

    /**
     * Mock大模型返回的内容。 真实调用应该取消该调用
     * @param modelId 大模型ID
     * @param sampleId 评测题目ID
     * @return 大模型返回的内容
     */
    private String mockModelOutput(Long modelId, Long sampleId) {
        List<MockModelOutputContentDO> records = mockModelOutputContentService.list(new LambdaQueryWrapper<MockModelOutputContentDO>()
                .eq(MockModelOutputContentDO::getBindModelId, modelId)
                .eq(MockModelOutputContentDO::getBindSampleId, sampleId)
                .eq(MockModelOutputContentDO::getDeleted, false));
        return records == null || records.isEmpty() ? null : records.get(0).getOutputText();
    }

    /**
     * 构建模型调用输入快照
     * @param modelInfo
     * @param resultDetail
     * @return
     */
    private Object buildModelCallInputSnapshot(ClientModelInfo modelInfo, EvalResultDetailDO resultDetail) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelId", modelInfo.getModelId());
        snapshot.put("manufacturerType", modelInfo.getManufacturerType());
        snapshot.put("model", modelInfo.getModel());
        snapshot.put("inputText", resultDetail.getInputText());
        return snapshot;
    }

    /**
     * 构建模型调用输出快照
     * @param modelResponse 模型调用响应
     * @return 模型调用输出快照
     */
    private Object buildModelCallOutputSnapshot(ModelResponse modelResponse) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelId", modelResponse.getModelId());
        snapshot.put("respContent", modelResponse.getRespContent());
        snapshot.put("elapsed", modelResponse.getElapsed());
        return snapshot;
    }

    /**
     * 构建模型调用节点结果
     * @param modelResponse 模型调用响应
     * @return 模型调用节点结果
     */
    private Object buildModelCallNodeResult(ModelResponse modelResponse) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("success", true);
        nodeResult.put("elapsed", modelResponse.getElapsed());
        return nodeResult;
    }

    /**
     * 构建模型调用失败节点结果
     * @param decision 失败决策
     * @return 失败节点结果
     */
    private Object buildFailedNodeResult(String decision) {
        LinkedHashMap<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("success", false);
        nodeResult.put("decision", decision);
        return nodeResult;
    }

}
