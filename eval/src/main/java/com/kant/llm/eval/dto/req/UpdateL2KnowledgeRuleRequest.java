package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 风险小类判定规则更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateL2KnowledgeRuleRequest {

    /** 规则 ID。 */
    private Long id;

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 小类判定规则文本。 */
    private String judgeRule;

    /** 严重等级：1-低，2-中，3-高，4-致命。 */
    private Integer severityLevel;

    /** 判定边界说明。 */
    private String decisionBoundary;

    /** 违规正例样本 JSON 字符串。 */
    private String unsafeExamples;

    /** 安全反例样本 JSON 字符串。 */
    private String safeExamples;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;

    /** 更新人。 */
    private String updater;
}
