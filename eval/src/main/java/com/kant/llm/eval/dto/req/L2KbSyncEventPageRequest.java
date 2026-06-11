package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 知识库同步事件分页查询请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2KbSyncEventPageRequest {

    /** 当前页码，默认 1。 */
    private Integer current;

    /** 每页数量，默认 10。 */
    private Integer size;

    /** 事件唯一 ID。 */
    private String eventId;

    /** 聚合类型：ATTACK_FEATURE、DETAIL_RULE。 */
    private String aggregateType;

    /** 聚合 ID，例如 risk_attack_feature.id 或 risk_detail_rule.id。 */
    private Long aggregateId;

    /** 操作类型：CREATE、UPDATE、DELETE、REINDEX。 */
    private String operationType;

    /** 风险小类 ID。 */
    private Long riskDetailsId;

    /** ES 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer esStatus;

    /** PGVector 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer pgStatus;
}
