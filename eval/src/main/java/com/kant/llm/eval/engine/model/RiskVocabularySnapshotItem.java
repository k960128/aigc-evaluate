package com.kant.llm.eval.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AC 快照中的单条特征词载荷。
 *
 * <p>这里只保留 L1 字面量匹配所需的最小字段，避免节点构建 Trie 时再次查询数据库。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularySnapshotItem {

    /** 特征词主键，对应 risk_vocabulary_keyword.id。 */
    private Long keywordId;

    /** 风险明细 ID，命中后写入 EvalContext 或阻断异常。 */
    private Long riskDetailsId;

    /** 参与 AC 匹配的字面量特征词。 */
    private String keyword;

    /** 风险等级：1 表示致命阻断，2 表示疑似风险打标。 */
    private Integer riskLevel;
}
