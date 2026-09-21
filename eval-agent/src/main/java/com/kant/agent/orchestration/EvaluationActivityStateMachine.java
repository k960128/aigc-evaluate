package com.kant.agent.orchestration;

import com.kant.agent.contract.approval.ApprovalDecision;
import com.kant.agent.contract.evaluation.EvaluationActivity;
import com.kant.agent.contract.evaluation.EvaluationActivityStatus;
import com.kant.agent.contract.evaluation.EvaluationExecutionSnapshot;

import java.util.Map;
import java.util.Set;

import static com.kant.agent.contract.evaluation.EvaluationActivityStatus.*;

/**
 * 评测活动状态迁移规则。
 *
 * <p>只校验，不持久化。
 * M1 必须在事务和并发版本控制下执行状态更新。</p>
 */
public final class EvaluationActivityStateMachine {

    private static final Map<
            EvaluationActivityStatus,
            Set<EvaluationActivityStatus>
            > ALLOWED = Map.of(
            DRAFT, Set.of(PLANNING, CANCELLED),
            PLANNING, Set.of(WAITING_APPROVAL, FAILED, CANCELLED),
            WAITING_APPROVAL, Set.of(
                    PLANNING, READY_TO_SUBMIT, CANCELLED
            ),
            READY_TO_SUBMIT, Set.of(SUBMITTING, CANCELLED),
            SUBMITTING, Set.of(WAITING_EVALUATION, FAILED),
            WAITING_EVALUATION, Set.of(COMPLETED, FAILED, CANCELLED)
    );

    private final PlanApprovalValidator approvalValidator =
            new PlanApprovalValidator();

    /**
     * 校验不需要流水线状态证明的迁移。
     */
    public ValidationResult validateTransition(
            EvaluationActivity activity,
            EvaluationActivityStatus next
    ) {
        return validateTransition(activity, next, null);
    }

    /**
     * 校验一次状态迁移。
     *
     * @param activity 当前活动快照
     * @param next     目标状态
     * @param execution 指定执行批次的状态；
     *                  从 WAITING_EVALUATION 进入终态时必须提供
     */
    public ValidationResult validateTransition(
            EvaluationActivity activity,
            EvaluationActivityStatus next,
            EvaluationExecutionSnapshot execution
    ) {
        if (activity == null || next == null) {
            return ValidationResult.failure(
                    "status", "STATUS_INPUT_REQUIRED",
                    "活动和目标状态不能为空"
            );
        }

        Set<EvaluationActivityStatus> allowed =
                ALLOWED.getOrDefault(activity.status(), Set.of());

        if (!allowed.contains(next)) {
            return ValidationResult.failure(
                    "status", "ILLEGAL_TRANSITION",
                    "不允许从 " + activity.status() + " 变为 " + next
            );
        }

        if (next == WAITING_APPROVAL && activity.currentPlan() == null) {
            return ValidationResult.failure(
                    "plan", "PLAN_REQUIRED",
                    "形成正式计划后才能进入人工审批"
            );
        }

        if (activity.status() == WAITING_APPROVAL && next == PLANNING) {
            var approval = activity.lastApproval();

            if (approval == null
                    || !approval.matches(activity.currentPlan())
                    || approval.decision()
                    != ApprovalDecision.RETURN_FOR_REVISION) {
                return ValidationResult.failure(
                        "approval", "REVISION_APPROVAL_REQUIRED",
                        "需要针对当前计划的退回修改记录"
                );
            }
        }

        if (next == READY_TO_SUBMIT
                || next == SUBMITTING
                || next == WAITING_EVALUATION) {
            ValidationResult approvalResult = approvalValidator.validate(
                    activity.currentPlan(), activity.lastApproval()
            );
            if (!approvalResult.passed()) {
                return approvalResult;
            }
        }

        if (next == WAITING_EVALUATION
                && activity.evaluationTask() == null) {
            return ValidationResult.failure(
                    "evaluationTask", "TASK_REFERENCE_REQUIRED",
                    "尚未获得本次提交的准确执行批次"
            );
        }

        if (activity.status() == WAITING_EVALUATION) {
            if (execution == null || activity.evaluationTask() == null) {
                return ValidationResult.failure(
                        "execution", "EXECUTION_SNAPSHOT_REQUIRED",
                        "需要指定执行批次的真实状态"
                );
            }

            if (!activity.evaluationTask().equals(execution.reference())) {
                return ValidationResult.failure(
                        "execution.reference", "BATCH_MISMATCH",
                        "执行状态来自其他评测批次"
                );
            }

            boolean matches = switch (next) {
                case COMPLETED -> execution.state()
                        == EvaluationExecutionSnapshot.State.COMPLETED;
                case FAILED -> execution.state()
                        == EvaluationExecutionSnapshot.State.FAILED;
                case CANCELLED -> execution.state()
                        == EvaluationExecutionSnapshot.State.STOPPED;
                default -> false;
            };

            if (!matches) {
                return ValidationResult.failure(
                        "execution", "EXECUTION_STATE_MISMATCH",
                        "当前流水线状态不支持该活动状态迁移"
                );
            }
        }

        return ValidationResult.success();
    }
}