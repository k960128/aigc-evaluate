package com.kant.agent.contract.evaluation;

/**
 * 智能体侧评测活动状态。
 */
public enum EvaluationActivityStatus {

    /** 活动已创建，尚未开始规划。 */
    DRAFT,

    /** 正在生成或修订计划。 */
    PLANNING,

    /** 计划已形成，等待人工审批。 */
    WAITING_APPROVAL,

    /** 当前版本已批准，等待提交。 */
    READY_TO_SUBMIT,

    /** 正在创建并提交现有评测任务。 */
    SUBMITTING,

    /** 已关联执行批次，等待现有流水线完成。 */
    WAITING_EVALUATION,

    /** 当前阶段的评测活动已完成。 */
    COMPLETED,

    /** 活动失败。 */
    FAILED,

    /** 活动被取消、拒绝，或者关联执行批次已停止。 */
    CANCELLED;

    /**
     * 是否为终态。
     */
    public boolean isTerminal() {
        return this == COMPLETED
                || this == FAILED
                || this == CANCELLED;
    }
}