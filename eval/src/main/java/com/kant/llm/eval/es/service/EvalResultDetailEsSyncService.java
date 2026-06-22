package com.kant.llm.eval.es.service;

/**
 * 评测结果明细 ES 投影同步服务。
 */
public interface EvalResultDetailEsSyncService {

    /**
     * 回查 MySQL 最新评测结果明细并覆盖写入 ES。
     *
     * @param resultDetailId 评测结果明细 ID
     */
    void syncUpsert(Long resultDetailId);
}
