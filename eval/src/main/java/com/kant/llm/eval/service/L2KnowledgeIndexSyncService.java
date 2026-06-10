package com.kant.llm.eval.service;

/**
 * L2 知识库索引同步服务。
 */
public interface L2KnowledgeIndexSyncService {

    /**
     * 同步一批待处理知识库事件。
     *
     * @return 实际处理的事件数量
     */
    int syncPendingEvents();
}
