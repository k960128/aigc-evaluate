package com.kant.agent.contract.redteam;

/**
 * 自动红队目标会话状态。
 */
public enum AttackSessionStatus {

    /** 会话记录已创建，尚未完成首次目标调用。 */
    OPENING,

    /** 可以继续在同一目标会话中发送下一轮输入。 */
    ACTIVE,

    /** 已收到暂停意图，等待当前动作边界收敛。 */
    PAUSING,

    /** 已到达可恢复检查点，不再安排新的目标调用。 */
    PAUSED,

    /** 因目标结果未知而阻止继续推进。 */
    BLOCKED,

    /** 用户停止或任务终止后关闭。 */
    STOPPED,

    /** 会话已完成本次有界探索。 */
    COMPLETED,

    /** 目标会话已经过期，不能继续追加消息。 */
    EXPIRED,

    /** 会话因不可恢复错误结束。 */
    FAILED,

    /** 平台已执行关闭操作。 */
    CLOSED
}
