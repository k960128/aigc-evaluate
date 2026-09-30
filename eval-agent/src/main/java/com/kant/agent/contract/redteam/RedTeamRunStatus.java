package com.kant.agent.contract.redteam;

/**
 * 自动红队运行状态。
 */
public enum RedTeamRunStatus {

    /** 已创建运行记录但尚未开始循环。 */
    QUEUED,

    /** 正在推进攻击动作。 */
    RUNNING,

    /** 因未知目标结果或证据缺口暂时不能安全推进。 */
    BLOCKED,

    /** 在有界条件下正常结束。 */
    COMPLETED,

    /** 因不可恢复错误结束。 */
    FAILED,

    /** 用户主动停止。 */
    STOPPED
}
