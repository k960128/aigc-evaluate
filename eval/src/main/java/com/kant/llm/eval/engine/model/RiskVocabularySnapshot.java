package com.kant.llm.eval.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次风险词库发布生成的不可变全量快照。
 *
 * <p>集群节点构建 AC 自动机时只读取某个明确版本的快照，不直接读取实时 DB 数据。
 * 这样能保证同一版本在不同节点上的构建输入一致。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularySnapshot {

    /** 本次发布生成的版本号。 */
    private Long versionId;

    /** 基于快照内容计算的 SHA-256 hash。 */
    private String hash;

    /** 去重、过滤后的特征词数量。 */
    private Integer wordCount;

    /** 快照发布时间。 */
    private LocalDateTime publishTime;

    /** 进入 AC 构建的全量特征词明细。 */
    @Builder.Default
    private List<RiskVocabularySnapshotItem> items = new ArrayList<>();
}
