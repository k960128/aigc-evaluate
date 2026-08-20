package com.kant.llm.eval.knowledge.mq.v2.consumer;

import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.knowledge.mq.v2.message.DocumentPublishVersionMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.KNOWLEDGE_PUBLISH_VERSION_TOPIC,
        consumerGroup = "document-publish-version-message-consumer-group"
)
public class DocumentPublishVersionConsumer
        implements RocketMQListener<MessageWrapper<DocumentPublishVersionMessage>> {

    @Override
    public void onMessage(MessageWrapper<DocumentPublishVersionMessage> messageWrapper) {
        DocumentPublishVersionMessage message = messageWrapper.getBody();
        log.info("[消费者] bizDesc: {}，topic: {}, riskDetailsId: {}, versionId: {}", messageWrapper.getBizDesc(),
                EvalMqTopics.KNOWLEDGE_PUBLISH_VERSION_TOPIC, message.getRiskDetailsId(), message.getVersionId());
    }
}
