package com.kant.llm.eval.es.mq.consumer;

import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.common.enums.EvalEsSyncAggregateTypeEnums;
import com.kant.llm.common.enums.EvalEsSyncOperationTypeEnums;
import com.kant.llm.common.enums.EvalEsSyncStatusEnums;
import com.kant.llm.eval.es.dao.entity.EvalEsSyncEventDO;
import com.kant.llm.eval.es.dao.mapper.EvalEsSyncEventMapper;
import com.kant.llm.eval.es.mq.message.EvalEsSyncEventMessage;
import com.kant.llm.eval.es.service.EvalEsSyncEventService;
import com.kant.llm.eval.es.service.EvalPipelineNodeDetailEsSyncService;
import com.kant.llm.eval.es.service.EvalResultDetailEsSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 评测 ES 投影同步事件消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = EvalMqTopics.EVAL_ES_SYNC_EVENT_TOPIC,
        consumerGroup = "aigc-eval-es-sync-event-consumer-group"
)
public class EvalEsSyncConsumer implements RocketMQListener<MessageWrapper<EvalEsSyncEventMessage>> {

    private final EvalEsSyncEventMapper evalEsSyncEventMapper;
    private final EvalEsSyncEventService evalEsSyncEventService;
    private final EvalResultDetailEsSyncService evalResultDetailEsSyncService;
    private final EvalPipelineNodeDetailEsSyncService evalPipelineNodeDetailEsSyncService;

    @Override
    public void onMessage(MessageWrapper<EvalEsSyncEventMessage> messageWrapper) {
        String eventId = messageWrapper == null || messageWrapper.getBody() == null
                ? null
                : messageWrapper.getBody().getEventId();
        if (eventId == null || eventId.isBlank()) {
            log.warn("评测 ES 投影同步消息缺少 eventId，messageWrapper: {}", messageWrapper);
            return;
        }
        EvalEsSyncEventDO event = evalEsSyncEventMapper.selectById(eventId);
        if (event == null) {
            log.warn("评测 ES 投影同步事件不存在，eventId: {}", eventId);
            return;
        }
        if (EvalEsSyncStatusEnums.SYNCED.getCode().equals(event.getSyncStatus())) {
            log.info("评测 ES 投影同步事件已完成，跳过重复消息，eventId: {}", eventId);
            return;
        }
        try {
            syncEvent(event);
            evalEsSyncEventService.markSynced(eventId);
            log.info("评测 ES 投影同步事件处理成功，eventId: {}, aggregateType: {}, aggregateId: {}",
                    eventId, event.getAggregateType(), event.getAggregateId());
        } catch (Exception ex) {
            evalEsSyncEventService.markFailed(eventId, ex);
            log.warn("评测 ES 投影同步事件处理失败，已记录重试信息，eventId: {}, aggregateType: {}, aggregateId: {}, reason: {}",
                    eventId, event.getAggregateType(), event.getAggregateId(), ex.getMessage(), ex);
        }
    }

    /**
     * 按聚合类型回查 MySQL 最新数据并写入对应 ES 索引。
     */
    private void syncEvent(EvalEsSyncEventDO event) {
        if (!EvalEsSyncOperationTypeEnums.UPSERT.getCode().equals(event.getOperationType())) {
            throw new IllegalArgumentException("暂不支持的评测 ES 投影操作类型：" + event.getOperationType());
        }
        EvalEsSyncAggregateTypeEnums aggregateType = EvalEsSyncAggregateTypeEnums.of(event.getAggregateType());
        if (aggregateType == null) {
            throw new IllegalArgumentException("未知的评测 ES 投影聚合类型：" + event.getAggregateType());
        }
        switch (aggregateType) {
            case RESULT_DETAIL -> evalResultDetailEsSyncService.syncUpsert(event.getAggregateId());
            case PIPELINE_NODE -> evalPipelineNodeDetailEsSyncService.syncUpsert(event.getAggregateId());
        }
    }
}
