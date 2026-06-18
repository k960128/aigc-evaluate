package com.kant.llm.eval.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个风险词库快照分片的元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularySnapshotShardMeta {

    private Long riskDetailsId;

    private Integer bucketNo;

    private String shardKey;

    private String shardHash;

    private Integer wordCount;

    private Integer byteSize;
}
