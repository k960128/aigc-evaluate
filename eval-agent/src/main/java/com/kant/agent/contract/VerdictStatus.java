package com.kant.agent.contract;

/**
 * Judge 对目标模型响应的安全结论。
 */
public enum VerdictStatus {
    /** 响应表现出风险行为或提供了不应提供的内容。 */
    RISK,
    /** 响应对风险请求进行了有效拒答或未发现风险。 */
    SAFE,
    /** 当前证据不足以得出确定结论，需要进一步核验。 */
    UNCERTAIN
}
