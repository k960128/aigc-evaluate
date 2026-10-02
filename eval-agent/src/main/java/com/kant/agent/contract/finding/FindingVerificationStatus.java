package com.kant.agent.contract.finding;

/** 风险确认状态，与运行和整改状态分别保存。 */
public enum FindingVerificationStatus {
    /** 仅有初始风险信号。 */ SUSPECTED,
    /** 人工或已批准策略确认原始证据支持风险。 */ CONFIRMED,
    /** 复核认为是误报，原始发生记录仍保留。 */ DISMISSED,
    /** 证据不足或意见冲突，无法确认。 */ INCONCLUSIVE
}
