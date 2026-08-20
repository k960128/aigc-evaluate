package com.kant.llm.eval.knowledge.mq.v1.consumer;

import com.kant.framework.idempotent.IdempotentConsume;
import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.knowledge.engine.L1InterceptionEngine;
import com.kant.llm.eval.knowledge.mq.v1.message.RiskVocabularyAcPublishMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * AC 快照构建和激活广播消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = EvalMqTopics.RISK_VOCABULARY_AC_PUBLISH_TOPIC,
        consumerGroup = "aigc-eval-risk-vocabulary-ac-publish-consumer-group",
        messageModel = MessageModel.BROADCASTING
)
public class RiskVocabularyAcPublishConsumer implements RocketMQListener<MessageWrapper<RiskVocabularyAcPublishMessage>> {

    private final L1InterceptionEngine l1InterceptionEngine;

    @Override
    @IdempotentConsume(
            keyPrefix = "risk:vocabulary:ac:consume:",
            key = "T(java.lang.management.ManagementFactory).getRuntimeMXBean().getName()"
                    + " + ':' + #message.body.messageType + ':' + #message.body.versionId + ':' + #message.body.globalHash",
            keyTimeout = 86_400L
    )
    public void onMessage(MessageWrapper<RiskVocabularyAcPublishMessage> message) {
        RiskVocabularyAcPublishMessage body = message == null ? null : message.getBody();
        if (body == null) {
            log.warn("\u8df3\u8fc7\u7a7a AC \u53d1\u5e03\u6d88\u606f");
            return;
        }
        log.info("\u6d88\u8d39 AC \u53d1\u5e03\u6d88\u606f\uff0ctype={}\uff0cversionId={}\uff0cmanifestKey={}",
                body.getMessageType(), body.getVersionId(), body.getManifestKey());
        if (RiskVocabularyAcPublishMessage.BUILD_VERSION.equals(body.getMessageType())) {
            l1InterceptionEngine.prepareFromManifest(body.getVersionId(), body.getManifestKey(), body.getGlobalHash());
            return;
        }
        if (RiskVocabularyAcPublishMessage.ACTIVATE_VERSION.equals(body.getMessageType())) {
            l1InterceptionEngine.activatePreparedVersion(body.getVersionId(), body.getGlobalHash());
            return;
        }
        log.warn("\u672a\u77e5 AC \u53d1\u5e03\u6d88\u606f\u7c7b\u578b\uff1a{}", body.getMessageType());
    }
}
