package com.kant.agent.contract.finding;

/** 已确认发现的整改状态；当前D04切片仅初始化OPEN，不允许直接声称修复。 */
public enum FindingRemediationStatus {
    /** 待处理。 */ OPEN,
    /** 处理中。 */ IN_PROGRESS,
    /** 待固定用例回归。 */ READY_FOR_RETEST,
    /** 通过当前指定回归且负责人确认。 */ RESOLVED,
    /** 再次出现。 */ REOPENED,
    /** 负责人接受风险，不是技术修复。 */ ACCEPTED_RISK
}
