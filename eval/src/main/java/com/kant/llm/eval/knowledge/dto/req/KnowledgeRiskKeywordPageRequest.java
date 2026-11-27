package com.kant.llm.eval.knowledge.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险词库特征词分页查询请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRiskKeywordPageRequest {

    /** 当前页码，默认 1。 */
    private Integer current;

    /** 每页数量，默认 10。 */
    private Integer size;

    /** 风险明细 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 特征词关键词，按 keyword 模糊查询。 */
    private String keyword;

    /** 匹配模式:   1-精确匹配,   2-正则匹配 */
    private Integer matchType;
}
