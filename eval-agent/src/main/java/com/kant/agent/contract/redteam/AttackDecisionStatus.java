package com.kant.agent.contract.redteam;

/**
 * 攻击 Agent 的决策结果状态。
 */
public enum AttackDecisionStatus {

    /** 已生成一个待编排器校验的动作。 */
    ACTION_PROPOSED,

    /** Agent 建议结束当前尝试。 */
    END_REQUESTED,

    /** Agent 调用失败或返回了无法解析的结果。 */
    GENERATION_FAILED
}
