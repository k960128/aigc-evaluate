package com.kant.llm.eval.pipeline.mq;

import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.pipeline.service.SafetyEvaluateExecuteService;
import com.kant.llm.eval.pipeline.record.SafetyEvaluateMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.SAFETY_EVALUATE_TOPIC,
        consumerGroup = "aigc-eval-safety-evaluate-consumer-group",
        maxReconsumeTimes = 2
)
public class SafetyEvaluateConsumer implements RocketMQListener<MessageWrapper<SafetyEvaluateMessage>> {

    /** 最大重试次数（不含首次消费，即总消费次数 = MAX_RETRY + 1） */
    private final int MAX_RETRY = 3;

    private final SafetyEvaluateExecuteService safetyEvaluateExecuteService;

    public SafetyEvaluateConsumer(SafetyEvaluateExecuteService safetyEvaluateExecuteService) {
        this.safetyEvaluateExecuteService = safetyEvaluateExecuteService;
    }

    @Override
    public void onMessage(MessageWrapper<SafetyEvaluateMessage> messageWrapper) {
        SafetyEvaluateMessage message = messageWrapper.getBody();
        log.info("[消费者] 开始处理安全评测消息，topic: {}, taskDetailId: {}, resultDetailId: {}, sampleId: {}, modelId: {}",
                EvalMqTopics.SAFETY_EVALUATE_TOPIC, message.getTaskDetailId(), message.getResultDetailId(),
                message.getSampleId(), message.getModelId());
        // 提交执行
        safetyEvaluateExecuteService.submitExecute(message);
    }
}
