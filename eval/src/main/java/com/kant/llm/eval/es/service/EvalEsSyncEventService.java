package com.kant.llm.eval.es.service;

/**
 * 评测 ES 投影同步事件状态服务。
 */
public interface EvalEsSyncEventService {

    /**
     * 标记同步事件处理成功。
     *
     * @param eventId 同步事件 ID
     */
    void markSynced(String eventId);

    /**
     * 标记同步事件处理失败，并计算下一次重试时间。
     *
     * @param eventId 同步事件 ID
     * @param ex 失败异常
     */
    void markFailed(String eventId, Exception ex);
}
