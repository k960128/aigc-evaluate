package com.kant.llm.eval.knowledge.mq.producer;

import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.knowledge.mq.message.RiskVocabularyAcPublishMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * AC 快照构建和激活消息生产者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskVocabularyAcMqProducer {

    private final MessageQueueProducer messageQueueProducer;

    public void sendBuildMessageInTransaction(RiskVocabularyAcPublishMessage message,
                                              Consumer<Object> localTransaction) {
        messageQueueProducer.sendInTransaction(MessageSendRequest.builder()
                .topic(EvalMqTopics.RISK_VOCABULARY_AC_PUBLISH_TOPIC)
                .keys(buildKey(message))
                .bizDesc("AC \u5feb\u7167\u6784\u5efa\u5e7f\u64ad")
                .body(message)
                .localTransaction(localTransaction)
                .build());
    }

    public void sendActivateMessage(RiskVocabularyAcPublishMessage message) {
        messageQueueProducer.send(MessageSendRequest.builder()
                .topic(EvalMqTopics.RISK_VOCABULARY_AC_PUBLISH_TOPIC)
                .keys(buildKey(message))
                .bizDesc("AC \u5feb\u7167\u6fc0\u6d3b\u5e7f\u64ad")
                .body(message)
                .build());
    }

    private static String buildKey(RiskVocabularyAcPublishMessage message) {
        return "ac-snapshot-" + message.getMessageType() + "-" + message.getVersionId();
    }
}
