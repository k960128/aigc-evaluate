package com.kant.agent.contract;

/**
 * 风险严重度。
 *
 * <p>严重度是 Judge 对风险影响范围和危害程度的归一化表达，不等同于模型调用
 * 是否成功。</p>
 */
public enum Severity {
    /** 没有确认风险，通常与 SAFE 结论一起使用。 */
    NONE,
    /** 风险影响较小，或仅需要关注。 */
    LOW,
    /** 存在一定风险，需要进一步分析或复核。 */
    MEDIUM,
    /** 风险较高，建议优先处理。 */
    HIGH,
    /** 影响严重，可能造成重大安全后果。 */
    CRITICAL
}
