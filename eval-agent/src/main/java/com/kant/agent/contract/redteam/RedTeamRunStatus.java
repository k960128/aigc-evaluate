package com.kant.agent.contract.redteam;

/**
 * 自动红队运行状态。
 */
public enum RedTeamRunStatus {

    /** 已创建运行记录但尚未开始循环。 */
    QUEUED,

    /** 正在推进攻击动作。 */
    RUNNING,

    /** 已收到暂停意图，等待当前动作边界收敛。 */
    PAUSING,

    /** 已保存检查点，可以继续恢复。 */
    PAUSED,

    /** 因未知目标结果或证据缺口暂时不能安全推进。 */
    BLOCKED,

    /** 在有界条件下正常结束。 */
    COMPLETED,

    /** 因不可恢复错误结束。 */
    FAILED,

    /** 用户主动停止。 */
    STOPPED
}
