package com.kant.agent.contract.redteam;

/**
 * 自动红队任务的停止原因。
 *
 * <p>停止原因和运行状态分开保存。运行完成不代表目标整体安全，
 * 这里只说明本次有界探索为什么停止。</p>
 */
public enum AttackStopReason {

    /** 当前响应满足预先配置的疑似风险信号。 */
    RISK_OBSERVED,

    /** 连续动作没有带来新的有效信息。 */
    NO_PROGRESS,

    /** 固定策略没有更多动作。 */
    STRATEGY_EXHAUSTED,

    /** 本次运行的目标调用预算已经用尽。 */
    BUDGET_EXHAUSTED,

    /** 目标调用已经发出，但最终结果无法确认。 */
    TARGET_RESULT_UNKNOWN,

    /** 目标结果可能已知，但本次模型用量无法结算，不能安全继续。 */
    BUDGET_USAGE_UNKNOWN,

    /** 目标调用明确失败，无法继续当前尝试。 */
    TARGET_EXECUTION_FAILED,

    /** 攻击 Agent 没有生成符合契约的动作。 */
    GENERATION_FAILED,

    /** Agent 提议的动作没有通过代码校验。 */
    ACTION_REJECTED,

    /** 用户请求停止运行。 */
    USER_STOPPED,

    /** 运行超过了任务允许的最长时间。 */
    TIME_LIMIT_REACHED
}
