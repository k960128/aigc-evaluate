package com.kant.llm.eval.service;

import com.kant.llm.eval.dto.resp.L2KnowledgeSyncSubmitVO;
import com.kant.llm.eval.mq.message.KbSyncEventMessage;
import com.kant.llm.eval.mq.message.L2KnowledgeSyncBatchMessage;

/**
 * L2 知识库索引同步服务。
 */
public interface L2KnowledgeSyncService {

    /**
     * 提交一键同步任务。
     */
    L2KnowledgeSyncSubmitVO submitPendingSync();

    /**
     * 扫描未同步知识并拆分成单条同步事件。
     */
    void dispatchPendingSync(L2KnowledgeSyncBatchMessage message);

    /**
     * 消费单条同步事件，写入 ES 和 PG 向量库。
     */
    void syncEvent(KbSyncEventMessage message);
}
