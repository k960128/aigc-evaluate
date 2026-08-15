package com.kant.llm.eval.knowledge.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 完整 AC 快照版本的清单。
 *
 * <p>清单只保存少量元数据和分片位置，节点会加载这里列出的全部分片，并合并构建一棵全局 AC 树。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularySnapshotManifest {

    private Long versionId;

    private String globalHash;

    private Integer totalWordCount;

    private String partitionType;

    private Integer shardCount;

    private LocalDateTime publishTime;

    @Builder.Default
    private List<RiskVocabularySnapshotShardMeta> shards = new ArrayList<>();
}
