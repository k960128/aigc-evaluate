package com.kant.agent.orchestration;

import com.kant.agent.contract.approval.ApprovalDecision;
import com.kant.agent.contract.approval.PlanApproval;
import com.kant.agent.contract.evaluation.EvaluationPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * 判断当前计划是否得到有效的人工批准。
 */
public final class PlanApprovalValidator {

    public ValidationResult validate(
            EvaluationPlan plan,
            PlanApproval approval
    ) {
        if (plan == null) {
            return ValidationResult.failure(
                    "plan", "PLAN_REQUIRED", "当前计划不存在"
            );
        }

        if (approval == null) {
            return ValidationResult.failure(
                    "approval", "APPROVAL_REQUIRED", "当前计划尚未审批"
            );
        }

        List<ValidationIssue> issues = new ArrayList<>();

        if (!approval.matches(plan)) {
            issues.add(new ValidationIssue(
                    "approval",
                    "APPROVAL_VERSION_MISMATCH",
                    "审批记录与当前计划版本或资源快照不匹配"
            ));
        }

        if (approval.decision() != ApprovalDecision.APPROVE) {
            issues.add(new ValidationIssue(
                    "approval.decision",
                    "PLAN_NOT_APPROVED",
                    "当前审批结果不是批准"
            ));
        }

        if (approval.decidedAt().isBefore(plan.createdAt())) {
            issues.add(new ValidationIssue(
                    "approval.decidedAt",
                    "INVALID_APPROVAL_TIME",
                    "审批时间不能早于计划创建时间"
            ));
        }

        return new ValidationResult(issues);
    }
}