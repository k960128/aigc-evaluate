package com.kant.llm.eval.es.service;

import java.util.Collection;

/**
 * 评测 ES 投影 outbox 记录服务。
 *
 * <p>业务代码只调用该服务记录“哪个聚合发生了变化”，不直接写 ES、不直接投递 MQ。
 * 真正写入 ES 的动作由调度器和消费者异步完成。</p>
 */
public interface EvalEsSyncOutboxService {

    /**
     * 记录评测结果明细 UPSERT 同步事件。
     *
     * @param resultDetailId 评测结果明细 ID
     */
    void recordResultDetailUpsert(Long resultDetailId);

    /**
     * 批量记录评测结果明细 UPSERT 同步事件。
     *
     * @param resultDetailIds 评测结果明细 ID 集合
     */
    void recordResultDetailUpsertBatch(Collection<Long> resultDetailIds);

    /**
     * 记录流水线节点日志 UPSERT 同步事件。
     *
     * @param nodeRecordId 流水线节点日志 ID
     */
    void recordPipelineNodeUpsert(Long nodeRecordId);
}
