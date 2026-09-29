package com.kant.agent.contract.probe;

/**
 * 自动红队的目标交互模式。
 */
public enum ExecutionMode {

    /** 每次输入都在独立、干净的目标上下文中执行。 */
    STATIC_SINGLE_TURN,

    /** 攻击侧参考历史反馈，但每次目标调用仍使用干净上下文。 */
    ADAPTIVE_SINGLE_TURN,

    /** 在同一目标会话内根据上一轮响应继续交互。 */
    ADAPTIVE_MULTI_TURN
}
