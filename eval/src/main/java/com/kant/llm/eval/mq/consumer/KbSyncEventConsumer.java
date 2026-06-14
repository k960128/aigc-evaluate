package com.kant.llm.eval.mq.consumer;

import com.kant.llm.eval.mq.EvalMqTopics;
import com.kant.llm.eval.mq.message.KbSyncEventMessage;
import com.kant.llm.eval.service.L2KnowledgeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 知识库单条索引同步事件消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = EvalMqTopics.L2_KB_SYNC_EVENT,
        consumerGroup = "aigc-eval-l2-kb-sync-event-consumer-group"
)
public class KbSyncEventConsumer implements RocketMQListener<KbSyncEventMessage> {

    private final L2KnowledgeSyncService l2KnowledgeSyncService;

    @Override
    public void onMessage(KbSyncEventMessage message) {
        log.info("开始消费 L2 知识库同步事件 MQ 消息，batchId: {}, eventId: {}",
                message == null ? null : message.getBatchId(),
                message == null ? null : message.getEventId());
        l2KnowledgeSyncService.syncEvent(message);
    }
}
