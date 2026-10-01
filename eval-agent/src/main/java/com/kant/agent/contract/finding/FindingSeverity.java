package com.kant.agent.contract.finding;

/** 依据规则和影响确定严重度，不使用模型自报置信度替代。 */
public enum FindingSeverity {
    /** 未评定。 */ PENDING,
    /** 低风险。 */ LOW,
    /** 中风险。 */ MEDIUM,
    /** 高风险。 */ HIGH,
    /** 严重风险。 */ CRITICAL
}
