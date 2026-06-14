package com.kant.llm.eval.mq.producer;

import com.kant.llm.eval.mq.EvalMqTopics;
import com.kant.llm.eval.mq.message.KbSyncEventMessage;
import com.kant.llm.eval.mq.message.L2KnowledgeSyncBatchMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 知识库索引同步 MQ 生产者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbSyncMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送 L2 知识库一键同步批次消息。
     */
    public void sendBatchSyncMessage(L2KnowledgeSyncBatchMessage message) {
        log.info("准备发送 L2 知识库同步批次 MQ 消息，topic: {}, batchId: {}",
                EvalMqTopics.L2_KB_SYNC_BATCH, message.getBatchId());
        try {
            rocketMQTemplate.convertAndSend(EvalMqTopics.L2_KB_SYNC_BATCH, message);
            log.info("L2 知识库同步批次 MQ 消息发送成功，topic: {}, batchId: {}",
                    EvalMqTopics.L2_KB_SYNC_BATCH, message.getBatchId());
        } catch (Exception ex) {
            log.error("L2 知识库同步批次 MQ 消息发送失败，topic: {}, batchId: {}",
                    EvalMqTopics.L2_KB_SYNC_BATCH, message.getBatchId(), ex);
            throw ex;
        }
    }

    /**
     * 发送单条知识库同步事件消息。
     */
    public void sendEventSyncMessage(KbSyncEventMessage message) {
        log.info("准备发送 L2 知识库同步事件 MQ 消息，topic: {}, batchId: {}, eventId: {}",
                EvalMqTopics.L2_KB_SYNC_EVENT, message.getBatchId(), message.getEventId());
        try {
            rocketMQTemplate.convertAndSend(EvalMqTopics.L2_KB_SYNC_EVENT, message);
            log.info("L2 知识库同步事件 MQ 消息发送成功，topic: {}, batchId: {}, eventId: {}",
                    EvalMqTopics.L2_KB_SYNC_EVENT, message.getBatchId(), message.getEventId());
        } catch (Exception ex) {
            log.error("L2 知识库同步事件 MQ 消息发送失败，topic: {}, batchId: {}, eventId: {}",
                    EvalMqTopics.L2_KB_SYNC_EVENT, message.getBatchId(), message.getEventId(), ex);
            throw ex;
        }
    }
}
