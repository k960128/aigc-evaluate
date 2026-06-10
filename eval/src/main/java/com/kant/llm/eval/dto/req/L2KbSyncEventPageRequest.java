package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 知识库同步事件分页查询请求。
 *
 * <p>同步事件用于串起 MySQL 事实源和后续 ES/Milvus 索引。
 * 当前阶段没有真实索引器时，也可以通过这些筛选条件查看本地事件是否按预期生成。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2KbSyncEventPageRequest {

    /** 当前页码，默认 1；传空或小于等于 0 时由服务层兜底。 */
    private Integer current;

    /** 每页数量，默认 10；传空或小于等于 0 时由服务层兜底。 */
    private Integer size;

    /** 同步事件唯一 ID，用于精确定位单次知识库变更。 */
    private String eventId;

    /** 聚合类型：ATTACK_FEATURE-攻击特征，DETAIL_RULE-风险小类规则。 */
    private String aggregateType;

    /** 聚合 ID，对应 risk_attack_feature.id 或 risk_detail_rule.id。 */
    private Long aggregateId;

    /** 操作类型：CREATE-新增，UPDATE-编辑，DELETE-删除，REINDEX-启停后重建索引。 */
    private String operationType;

    /** 风险小类 ID，用于按 risk_details 粒度排查同步事件。 */
    private Long riskDetailsId;

    /** ES 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer esStatus;

    /** Milvus 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer milvusStatus;
}
