package com.kant.agent.contract.evaluation;

import com.kant.agent.contract.approval.PlanApproval;

import static com.kant.agent.contract.common.ContractChecks.*;

/**
 * 智能体评测活动的业务快照。
 *
 * <p>状态迁移必须经过状态机校验。
 * M1 由应用服务负责持久化和并发控制。</p>
 *
 * @param activityId    活动唯一标识
 * @param intent        用户意图
 * @param status        智能体活动状态
 * @param currentPlan   当前计划；尚未形成计划时为空
 * @param lastApproval  最近审批记录；可以指向旧版本，是否有效需单独校验
 * @param evaluationTask 关联的现有执行批次；尚未提交时为空
 */
public record EvaluationActivity(
        String activityId,
        EvaluationIntent intent,
        EvaluationActivityStatus status,
        EvaluationPlan currentPlan,
        PlanApproval lastApproval,
        EvaluationTaskReference evaluationTask
) {

    public EvaluationActivity {
        activityId = text(activityId, "activityId");
        required(intent, "intent");
        required(status, "status");

        if (currentPlan != null
                && !activityId.equals(currentPlan.activityId())) {
            throw new IllegalArgumentException("计划不属于当前活动");
        }

        if (lastApproval != null
                && !activityId.equals(lastApproval.activityId())) {
            throw new IllegalArgumentException("审批记录不属于当前活动");
        }

        if (evaluationTask != null
                && !intent.targetModelId().equals(
                evaluationTask.targetModelId())) {
            throw new IllegalArgumentException("执行批次的目标模型与活动不一致");
        }

        if (currentPlan != null && evaluationTask != null
                && (!currentPlan.draft().targetModelId().equals(
                evaluationTask.targetModelId())
                || !currentPlan.draft().datasetId().equals(
                evaluationTask.datasetId()))) {
            throw new IllegalArgumentException("执行批次与当前计划不一致");
        }
    }
}