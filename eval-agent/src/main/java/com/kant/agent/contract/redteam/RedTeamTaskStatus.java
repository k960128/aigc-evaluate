package com.kant.agent.contract.redteam;

/**
 * 自动红队任务状态。
 */
public enum RedTeamTaskStatus {

    /** 已保存配置，等待启动。 */
    READY,

    /** 至少有一个运行正在处理。 */
    RUNNING,

    /** 最近一次运行正常结束。 */
    COMPLETED,

    /** 最近一次运行因不可恢复错误结束。 */
    FAILED,

    /** 运行被用户停止。 */
    STOPPED,

    /** 运行需要人工核对后才能继续。 */
    BLOCKED
}
