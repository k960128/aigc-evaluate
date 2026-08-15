package com.kant.llm.eval.es.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.llm.common.enums.EvalEsSyncStatusEnums;
import com.kant.llm.eval.es.dao.entity.EvalEsSyncEventDO;
import com.kant.llm.eval.es.dao.mapper.EvalEsSyncEventMapper;
import com.kant.llm.eval.es.service.EvalEsSyncEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 评测 ES 投影同步事件状态服务实现。
 */
@Service
@RequiredArgsConstructor
public class EvalEsSyncEventServiceImpl implements EvalEsSyncEventService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final EvalEsSyncEventMapper evalEsSyncEventMapper;

    @Override
    public void markSynced(String eventId) {
        evalEsSyncEventMapper.update(null, new LambdaUpdateWrapper<EvalEsSyncEventDO>()
                .eq(EvalEsSyncEventDO::getEventId, eventId)
                .set(EvalEsSyncEventDO::getSyncStatus, EvalEsSyncStatusEnums.SYNCED.getCode())
                .set(EvalEsSyncEventDO::getLastError, null));
    }

    @Override
    public void markFailed(String eventId, Exception ex) {
        EvalEsSyncEventDO event = evalEsSyncEventMapper.selectById(eventId);
        int nextRetryCount = event == null || event.getRetryCount() == null
                ? 1
                : event.getRetryCount() + 1;
        evalEsSyncEventMapper.update(null, new LambdaUpdateWrapper<EvalEsSyncEventDO>()
                .eq(EvalEsSyncEventDO::getEventId, eventId)
                .set(EvalEsSyncEventDO::getSyncStatus, EvalEsSyncStatusEnums.FAILED.getCode())
                .set(EvalEsSyncEventDO::getRetryCount, nextRetryCount)
                .set(EvalEsSyncEventDO::getNextRetryTime, LocalDateTime.now().plusMinutes(nextRetryDelayMinutes(nextRetryCount)))
                .set(EvalEsSyncEventDO::getLastError, trimErrorMessage(ex == null ? null : ex.getMessage())));
    }

    /**
     * 使用轻量指数退避，避免 ES 故障时调度器持续高频打满错误事件。
     */
    private long nextRetryDelayMinutes(int retryCount) {
        int cappedRetryCount = Math.min(retryCount, 5);
        return Math.min(30L, 1L << cappedRetryCount);
    }

    /**
     * 错误信息落库前做长度保护，避免远端异常栈或响应体过大。
     */
    private String trimErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "评测 ES 投影同步失败";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
