package com.kant.agent.contract.probe;

/**
 * 单次物理探测执行状态。
 */
public enum ProbeExecutionStatus {

    /** 已持久化执行意图，尚未开始目标调用。 */
    ACCEPTED,

    /** 正在调用目标。 */
    RUNNING,

    /** 已确认收到目标响应并完成证据保存。 */
    SUCCEEDED,

    /** 已确认目标调用前或目标调用本身明确失败。 */
    FAILED,

    /** 目标可能已经执行，但系统无法确认最终结果。 */
    UNKNOWN,

    /** 已收到取消意图，但目标调用不一定能立即终止。 */
    CANCELLATION_REQUESTED,

    /** 在目标调用开始前成功取消。 */
    CANCELLED;

    /**
     * 判断当前状态是否已经结束。
     *
     * @return 结束状态返回 {@code true}
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == UNKNOWN || this == CANCELLED;
    }
}
