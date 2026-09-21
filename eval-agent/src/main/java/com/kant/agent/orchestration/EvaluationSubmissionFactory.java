package com.kant.agent.orchestration;

import com.kant.agent.contract.evaluation.EvaluationActivity;
import com.kant.agent.contract.evaluation.EvaluationActivityStatus;
import com.kant.agent.contract.evaluation.EvaluationResources;
import com.kant.agent.contract.evaluation.EvaluationSubmissionCommand;

import static com.kant.agent.contract.common.ContractChecks.required;

/**
 * 根据已批准计划构建提交命令。
 *
 * <p>不调用流水线，不写数据库，不修改活动状态。</p>
 */
public final class EvaluationSubmissionFactory {

    private final EvaluationPlanValidator planValidator =
            new EvaluationPlanValidator();

    private final PlanApprovalValidator approvalValidator =
            new PlanApprovalValidator();

    public EvaluationSubmissionCommand create(
            EvaluationActivity activity,
            EvaluationResources currentResources
    ) {
        required(activity, "activity");

        if (activity.status()
                != EvaluationActivityStatus.READY_TO_SUBMIT) {
            throw new IllegalArgumentException("活动尚未准备好提交");
        }

        if (activity.evaluationTask() != null) {
            throw new IllegalArgumentException("活动已经关联执行批次");
        }

        var plan = activity.currentPlan();

        approvalValidator.validate(
                plan, activity.lastApproval()
        ).throwIfInvalid();

        planValidator.validate(
                activity.intent(), currentResources, plan
        ).throwIfInvalid();

        // planId 由后端生成并保证唯一。
        // 同一计划版本多次构建命令时，幂等键保持一致。
        String idempotencyKey =
                "eval-agent:" + plan.planId() + ":" + plan.version();

        return new EvaluationSubmissionCommand(
                idempotencyKey,
                activity.activityId(),
                plan.planId(),
                plan.version(),
                plan.draft().targetModelId(),
                plan.draft().datasetId(),
                plan.datasetContentFingerprint(),
                plan.plannedSampleCount(),
                plan.budget().maxTargetCalls(),
                activity.lastApproval().approvalId()
        );
    }
}