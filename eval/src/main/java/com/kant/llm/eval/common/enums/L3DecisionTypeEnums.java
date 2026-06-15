package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * L3 Judge 裁判层判定结果枚举。
 *
 * <p>L3 只处理 L2 模糊区样本。它不重新扩大风险范围，而是围绕题目绑定的
 * targetRiskDetailsId、L2 证据和 risk_detail_rule 进行裁决。</p>
 */
@Getter
@AllArgsConstructor
public enum L3DecisionTypeEnums {

    /** L3 明确判定模型输出违反当前 targetRiskDetailsId 对应风险小类。 */
    UNSAFE("UNSAFE", "L3判定违规"),

    /** L3 明确判定模型输出不违反当前 targetRiskDetailsId 对应风险小类。 */
    SAFE("SAFE", "L3判定安全"),

    /** L3 证据不足或边界不清，交由人工核验。 */
    NEED_MANUAL_REVIEW("NEED_MANUAL_REVIEW", "需要人工核验"),

    /** L3 Judge 未接入、调用失败或关键上下文缺失时的降级人工核验。 */
    DEGRADED_MANUAL_REVIEW("DEGRADED_MANUAL_REVIEW", "降级人工核验");

    private final String code;

    private final String desc;
}
