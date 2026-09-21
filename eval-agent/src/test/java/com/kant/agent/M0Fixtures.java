package com.kant.agent;

import com.kant.agent.contract.approval.ApprovalDecision;
import com.kant.agent.contract.approval.PlanApproval;
import com.kant.agent.contract.common.ExecutionBudget;
import com.kant.agent.contract.evaluation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * M0 测试使用的固定业务数据。
 *
 * <p>这里只是测试数据，不访问任何外部系统。</p>
 */
final class M0Fixtures {

    static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    private M0Fixtures() {
    }

    static ExecutionBudget budget() {
        return new ExecutionBudget(100, 120, 10, 20_000, 3);
    }

    static EvaluationIntent intent() {
        return new EvaluationIntent(
                "评测客服模型的提示词注入风险",
                1L,
                Set.of(10L),
                Set.of(20L),
                budget()
        );
    }

    static EvaluationResources resources() {
        return resources(10, Set.of(10L), 0, "fingerprint-v1");
    }

    static EvaluationResources resources(
            int sampleCount,
            Set<Long> datasetRisks,
            int unclassifiedCount,
            String fingerprint
    ) {
        return new EvaluationResources(
                "snapshot-1",
                NOW,
                1L,
                "客服测试模型",
                List.of(new EvaluationResources.RiskResource(
                        10L, "提示词注入", "检查指令边界"
                )),
                List.of(new EvaluationResources.DatasetResource(
                        20L,
                        "提示词注入测试集",
                        sampleCount,
                        datasetRisks,
                        unclassifiedCount,
                        fingerprint
                ))
        );
    }

    static EvaluationPlanDraft draft() {
        return new EvaluationPlanDraft(
                1L,
                20L,
                Set.of(10L),
                "评测提示词注入风险",
                "数据集覆盖所需风险，数量在预算内"
        );
    }

    static EvaluationPlan plan() {
        return plan(1);
    }

    static EvaluationPlan plan(int version) {
        return new EvaluationPlan(
                "plan-1",
                "activity-1",
                version,
                draft(),
                budget(),
                "snapshot-1",
                "fingerprint-v1",
                10,
                NOW,
                "run-1"
        );
    }

    static PlanApproval approval() {
        return approval(plan(), ApprovalDecision.APPROVE);
    }

    static PlanApproval approval(
            EvaluationPlan plan,
            ApprovalDecision decision
    ) {
        return new PlanApproval(
                "approval-" + plan.version(),
                plan.activityId(),
                plan.planId(),
                plan.version(),
                plan.resourceSnapshotId(),
                plan.datasetContentFingerprint(),
                decision,
                100L,
                NOW.plusSeconds(10),
                decision == ApprovalDecision.APPROVE
                        ? ""
                        : "请根据人工意见处理"
        );
    }

    static EvaluationTaskReference reference() {
        return new EvaluationTaskReference(
                1000L, 2000L, 20L, 1L
        );
    }

    static EvaluationActivity activity(
            EvaluationActivityStatus status,
            PlanApproval approval,
            EvaluationTaskReference reference
    ) {
        return new EvaluationActivity(
                "activity-1",
                intent(),
                status,
                plan(),
                approval,
                reference
        );
    }

    static EvaluationExecutionSnapshot execution(
            EvaluationTaskReference reference,
            Integer statusCode
    ) {
        return new EvaluationExecutionSnapshot(
                reference,
                statusCode,
                10L,
                10L,
                0L,
                NOW.plusSeconds(30)
        );
    }
}