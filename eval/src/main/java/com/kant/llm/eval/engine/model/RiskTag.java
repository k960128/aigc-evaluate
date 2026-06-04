package com.kant.llm.eval.engine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 挂载在 Aho-Corasick 自动机叶子节点上的轻量级载荷。
 *
 * <p>L1 引擎不再使用 boolean 表示是否命中，而是将风险元数据直接存入 Trie 的 value。
 * 字面量命中后，扫描线程可以立即根据风险等级和风险明细 ID 完成路由，
 * 热路径无需额外查询数据库或二次查表。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskTag {

    /**
     * 特征词主键，对应 risk_vocabulary_keyword.id。
     */
    private Long keywordId;

    /**
     * 风险明细 ID，用于下游 L2/L3 评测与统计。
     */
    private Long riskDetailsId;

    /**
     * 风险等级：1 表示致命拦截，2 表示疑似打标透传。
     */
    private Integer riskLevel;
}
