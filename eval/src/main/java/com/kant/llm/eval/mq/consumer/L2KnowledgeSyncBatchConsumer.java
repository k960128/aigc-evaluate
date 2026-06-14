package com.kant.llm.eval.mq.consumer;

import com.kant.llm.eval.mq.EvalMqTopics;
import com.kant.llm.eval.mq.message.L2KnowledgeSyncBatchMessage;
import com.kant.llm.eval.service.L2KnowledgeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * L2 知识库一键同步批次消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = EvalMqTopics.L2_KB_SYNC_BATCH,
        consumerGroup = "aigc-eval-l2-kb-sync-batch-consumer-group"
)
public class L2KnowledgeSyncBatchConsumer implements RocketMQListener<L2KnowledgeSyncBatchMessage> {

    private final L2KnowledgeSyncService l2KnowledgeSyncService;

    @Override
    public void onMessage(L2KnowledgeSyncBatchMessage message) {
        log.info("开始消费 L2 知识库同步批次 MQ 消息，batchId: {}, triggerType: {}",
                message == null ? null : message.getBatchId(),
                message == null ? null : message.getTriggerType());
        l2KnowledgeSyncService.dispatchPendingSync(message);
    }
}
