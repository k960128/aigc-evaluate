package com.kant.llm.eval.es.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.common.enums.EvalEsSyncStatusEnums;
import com.kant.llm.eval.es.dao.entity.EvalEsSyncEventDO;
import com.kant.llm.eval.es.dao.mapper.EvalEsSyncEventMapper;
import com.kant.llm.eval.es.mq.message.EvalEsSyncEventMessage;
import com.kant.llm.eval.es.service.EvalEsSyncDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测 ES 投影同步事件调度服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalEsSyncDispatchServiceImpl implements EvalEsSyncDispatchService {

    private static final int DISPATCH_BATCH_SIZE = 100;

    private final EvalEsSyncEventMapper evalEsSyncEventMapper;
    private final MessageQueueProducer messageQueueProducer;

    @Override
//    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void dispatchPendingEvents() {
        List<EvalEsSyncEventDO> events = evalEsSyncEventMapper.selectList(new LambdaQueryWrapper<EvalEsSyncEventDO>()
                .in(EvalEsSyncEventDO::getSyncStatus,
                        EvalEsSyncStatusEnums.PENDING.getCode(),
                        EvalEsSyncStatusEnums.FAILED.getCode())
                .le(EvalEsSyncEventDO::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(EvalEsSyncEventDO::getCreateTime)
                .last("LIMIT " + DISPATCH_BATCH_SIZE));
        if (events == null || events.isEmpty()) {
            return;
        }
        for (EvalEsSyncEventDO event : events) {
            messageQueueProducer.send(MessageSendRequest.builder()
                    .topic(EvalMqTopics.EVAL_ES_SYNC_EVENT_TOPIC)
                    .keys(event.getEventId())
                    .bizDesc("评测 ES 投影同步事件")
                    .body(EvalEsSyncEventMessage.builder()
                            .eventId(event.getEventId())
                            .build())
                    .build());
        }
        log.info("评测 ES 投影同步事件调度完成，dispatchCount: {}", events.size());
    }
}
