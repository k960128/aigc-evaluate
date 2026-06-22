package com.kant.llm.eval.es.service;

/**
 * 流水线节点日志 ES 投影同步服务。
 */
public interface EvalPipelineNodeDetailEsSyncService {

    /**
     * 回查 MySQL 最新流水线节点日志并覆盖写入 ES。
     *
     * @param nodeRecordId 流水线节点日志 ID
     */
    void syncUpsert(Long nodeRecordId);
}
