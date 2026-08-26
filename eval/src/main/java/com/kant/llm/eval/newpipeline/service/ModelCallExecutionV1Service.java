package com.kant.llm.eval.newpipeline.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.ai.infra.client.*;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.framework.exception.ServiceException;
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
import com.kant.llm.eval.pipeline.mq.message.ModelCallMessage;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import com.kant.llm.eval.pipeline.service.EvalPipelineNodeRecorder;
import com.kant.llm.eval.pipeline.service.ratelimit.ModelCallPermit;
import com.kant.llm.eval.pipeline.service.ratelimit.ModelCallRateLimiter;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.service.EvalResultDetailService;
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
public class ModelCallExecutionV1Service {
    private static final String EXECUTION_LOCK_KEY_PREFIX = "EVAL:MODEL-CALL:LOCK:KEY:%s";

    private final RedissonClient redissonClient;
    private final ModelInfoService modelInfoService;
    private final MessageQueueProducer messageQueueProducer;
    private final ModelCallRateLimiter modelCallRateLimiter;
    private final EvalResultDetailService evalResultDetailService;
    private final EvalPipelineNodeRecorder evalPipelineNodeRecorder;
    private final ModelClientStrategyFactory modelClientStrategyFactory;
    private final MockModelOutputContentService mockModelOutputContentService;

    public ModelCallExecutionV1Service(RedissonClient redissonClient,
                                       ModelInfoService modelInfoService,
                                       MessageQueueProducer messageQueueProducer,
                                       ModelCallRateLimiter modelCallRateLimiter,
                                       EvalResultDetailService evalResultDetailService,
                                       EvalPipelineNodeRecorder evalPipelineNodeRecorder,
                                       ModelClientStrategyFactory modelClientStrategyFactory,
                                       MockModelOutputContentService mockModelOutputContentService) {
        this.redissonClient = redissonClient;
        this.modelInfoService = modelInfoService;
        this.messageQueueProducer = messageQueueProducer;
        this.modelCallRateLimiter = modelCallRateLimiter;
        this.evalResultDetailService = evalResultDetailService;
        this.evalPipelineNodeRecorder = evalPipelineNodeRecorder;
        this.modelClientStrategyFactory = modelClientStrategyFactory;
        this.mockModelOutputContentService = mockModelOutputContentService;
    }


    /**
     *  提交执行模型调用
     * @param message msg
     */
    public void submitExecute(ModelCallMessage message) {
        // 获取分布式锁
        String lockKey = buildLockKey(message.getResultDetailId());
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            // 尝试获取锁：等待10秒，持有30秒后自动释放
            boolean locked = rLock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.info("评测结果明细正在执行中，跳过重复模型调用消息，resultDetailId: {}",
                        message.getResultDetailId());
                return;
            }
            try {
                // 执行业务逻辑
                execute(message);
            } catch (Exception e) {
                log.error("业务执行异常: {}", lockKey, e);
                // 根据业务需要决定是否抛出或记录
                throw new ServiceException(String.format("处理失败，resultDetailId: %s", e.getMessage()));
            } finally {
                // 只有当前线程持有锁时才释放
                if (rLock.isHeldByCurrentThread()) {
                    rLock.unlock();
                    log.debug("释放锁成功: {}", lockKey);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断: {}", lockKey, e);
            throw new RuntimeException("获取锁被中断", e);
        }
    }

    /**
     *  执行模型调用
     * @param message msg
     */
    private void execute(ModelCallMessage message) {
        EvalResultDetailDO resultDetail = evalResultDetailService.getById(message.getResultDetailId());
        // 验证回写结果
        validateResultDetail(resultDetail);
        // 获取模型信息
        ModelInfoVO modelInfo = loadModelInfo(message.getModelId());
        ModelCallPermit acquire = null;
        try {
            // 获取分布式模型调用许可
            acquire = modelCallRateLimiter.acquire(modelInfo);
            // 执行模型调用逻辑
            ModelResponse modelResponse = modelCall(modelInfo, resultDetail);
            // 回写数据库
            writeBackToDB(resultDetail, modelResponse);

            // 发送SafeEval消息
            sendSafetyEvaluateMessage(message);
        } catch (Exception e) {
            log.error("获取模型调用许可失败: {}", modelInfo, e);
        } finally {
            // 无论如何都要释放模型调用许可
            acquire.release();
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
     * 构建分布式锁的Key
     * @param resultDetailId 回写结果ID
     * @return Key
     */
    private String buildLockKey(Long resultDetailId) {
        return String.format(EXECUTION_LOCK_KEY_PREFIX, resultDetailId);
    }

    private void validateResultDetail(EvalResultDetailDO resultDetail) {
        if (resultDetail == null) {
            throw new ServiceException("评测结果明细不存在");
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

    /**
     * 回写模型调用结果到数据库
     * @param resultDetailDO 样本
     * @param modelResponse 大模型返回结果
     */
    private void writeBackToDB(EvalResultDetailDO resultDetailDO, ModelResponse modelResponse) {
        resultDetailDO.setModelOutput(modelResponse.getRespContent());
        resultDetailDO.setRawResponse(JSON.toJSONString(modelResponse));
        resultDetailDO.setLatency(Math.toIntExact(modelResponse.getElapsed()));
        resultDetailDO.setErrorMsg(null);
        resultDetailDO.setStatus(EvalResultStatusEnums.MODEL_CALLED.getCode());
        evalResultDetailService.updateById(resultDetailDO);
    }
}
