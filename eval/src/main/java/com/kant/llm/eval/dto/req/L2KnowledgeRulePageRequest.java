package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 风险小类判定规则分页查询请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2KnowledgeRulePageRequest {

    /** 当前页码，默认 1。 */
    private Integer current;

    /** 每页数量，默认 10。 */
    private Integer size;

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 严重等级：1-低，2-中，3-高，4-致命。 */
    private Integer severityLevel;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;

    /** 判定规则关键词，支持模糊查询 judge_rule。 */
    private String keyword;
}
