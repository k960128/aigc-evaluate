package com.kant.agent.simple.metrics;

import java.util.List;
import java.util.Optional;

public interface BaselineRepository {
    /**
     * 保存一个 baseline 快照。
     */
    void save(BaselineSnapshot snapshot);

    /**
     * 获取指定 label 的最新 baseline 快照。
     * @param label 标签，如 p0-p1_baseline / after_dedup / after_budget
     * @return 最新 baseline 快照，若不存在则返回 Optional.empty
     */
    Optional<BaselineSnapshot> latestByLabel(String label);

    /**
     * 获取所有 baseline 快照。
     * @return 所有 baseline 快照列表，按 createdAt 降序排序
     */
    List<BaselineSnapshot> findAll();
}