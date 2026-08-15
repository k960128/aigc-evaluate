package com.kant.llm.eval.knowledge.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 缓存中存储单个风险小类桶词条的快照分片。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularySnapshotShard {

    private Long riskDetailsId;

    private Integer bucketNo;

    @Builder.Default
    private List<RiskVocabularySnapshotItem> items = new ArrayList<>();
}
