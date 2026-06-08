package com.kant.llm.eval.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Redis Pub/Sub 中传递的 AC 版本发布消息。
 *
 * <p>消息只携带版本号、快照 Key 和校验信息，不直接传输全量特征词，避免 Pub/Sub
 * 通道承载几十万词的 payload。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularyPublishMessage {

    /** 新发布的 AC 版本号。 */
    private Long versionId;

    /** Redis 中保存全量快照的 Key。 */
    private String snapshotKey;

    /** 快照 hash，节点加载后会再次校验。 */
    private String hash;

    /** 快照词条数量，作为轻量一致性校验。 */
    private Integer wordCount;

    /** 发布消息生成时间。 */
    private LocalDateTime publishTime;
}
