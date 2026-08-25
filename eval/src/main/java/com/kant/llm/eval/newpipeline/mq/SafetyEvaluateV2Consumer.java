package com.kant.llm.eval.newpipeline.mq;

import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.SAFETY_EVALUATE_TOPIC,
        consumerGroup = "aigc-eval-safety-evaluate-consumer-group"
)
public class SafetyEvaluateV2Consumer implements RocketMQListener<MessageWrapper<SafetyEvaluateMessage>> {

    @Override
    public void onMessage(MessageWrapper<SafetyEvaluateMessage> message) {

    }
}
