package com.kant.agent.contract.finding;
/** 一条复核意见，不等于发现的最终确认状态。 */
public enum FindingReviewOpinion {
    /** 证据支持风险。 */ SUPPORT,
    /** 证据反对原结论。 */ OPPOSE,
    /** 证据不足。 */ INSUFFICIENT,
    /** 建议独立执行复现。 */ REPLAY_REQUIRED
}
