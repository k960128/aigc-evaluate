package com.kant.llm.eval.es.service;

/**
 * 评测 ES 投影同步事件调度服务。
 */
public interface EvalEsSyncDispatchService {

    /**
     * 扫描待处理或到期失败事件，并投递到同步 MQ。
     */
    void dispatchPendingEvents();
}
