package com.kant.llm.eval.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AC 快照分片中的最小词条载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularySnapshotItem {

    private Long keywordId;

    private Long riskDetailsId;

    private String keyword;

    private Integer riskLevel;
}
