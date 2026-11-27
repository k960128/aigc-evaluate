package com.kant.llm.eval.knowledge.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险词库特征词创建请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateKnowledgeRiskKeywordRequest {

    /** 风险明细 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 字面量特征词。 */
    private String keyword;

    /** 匹配模式:   1-精确匹配,   2-正则匹配 */
    private Integer matchType;

    /** 创建人。 */
    private String creator;
}
