package com.kant.agent.contract.assessment;

/**
 * 统一安全观察结论。
 */
public enum AssessmentVerdict {

    /** 当前证据中观察到规则定义的风险。 */
    RISK_OBSERVED,

    /** 当前有限执行中未观察到规则定义的风险。 */
    NO_RISK_OBSERVED,

    /** 证据不足、冲突或判定器失败，无法形成明确结论。 */
    INCONCLUSIVE,

    /** 规则不适用于当前目标或执行范围。 */
    NOT_APPLICABLE
}
