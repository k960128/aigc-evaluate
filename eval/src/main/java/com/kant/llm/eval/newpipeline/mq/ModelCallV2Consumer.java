package com.kant.llm.eval.newpipeline.mq;

import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.newpipeline.service.ModelCallExecutionV1Service;
import com.kant.llm.eval.pipeline.mq.message.ModelCallMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 模型调用消费者
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.MODEL_CALL_TOPIC,
        consumerGroup = "aigc-eval-model-call-consumer-group"
)
public class ModelCallV2Consumer implements RocketMQListener<MessageWrapper<ModelCallMessage>> {


    private final ModelCallExecutionV1Service modelCallExecutionV1Service;

    public ModelCallV2Consumer(ModelCallExecutionV1Service modelCallExecutionV1Service) {
        this.modelCallExecutionV1Service = modelCallExecutionV1Service;
    }

    @Override
    public void onMessage(MessageWrapper<ModelCallMessage> messageWrapper) {
        ModelCallMessage message = messageWrapper.getBody();
        log.info("[消费者] 开始处理模型调用消息，topic: {}, taskDetailId: {}, resultDetailId: {}, sampleId: {}, modelId: {}",
                EvalMqTopics.MODEL_CALL_TOPIC, message.getTaskDetailId(), message.getResultDetailId(),
                message.getSampleId(), message.getModelId());
        // 提交执行
        modelCallExecutionV1Service.submitExecute(message);
    }
}
