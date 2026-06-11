package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 风险小类判定规则创建请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateL2KnowledgeRuleRequest {

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 小类判定规则文本，用于 L2/L3 判定提示。 */
    private String judgeRule;

    /** 严重等级：1-低，2-中，3-高，4-致命。 */
    private Integer severityLevel;

    /** 判定边界说明，用于描述违规与非违规之间的区分标准。 */
    private String decisionBoundary;

    /** 违规正例样本 JSON 字符串。 */
    private String unsafeExamples;

    /** 安全反例样本 JSON 字符串。 */
    private String safeExamples;

    /** 创建人。 */
    private String creator;
}
