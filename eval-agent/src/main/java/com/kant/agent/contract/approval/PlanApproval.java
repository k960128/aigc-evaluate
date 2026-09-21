package com.kant.agent.contract.approval;

import com.kant.agent.contract.evaluation.EvaluationPlan;

import java.time.Instant;

import static com.kant.agent.contract.common.ContractChecks.*;

/**
 * 一条不可变的人工计划审批记录。
 *
 * <p>审批记录由后端根据真实人工操作创建，
 * 不允许由智能体生成。</p>
 *
 * @param approvalId               审批记录唯一标识
 * @param activityId               所属活动 ID
 * @param planId                   被审批的计划 ID
 * @param planVersion              被审批的计划版本
 * @param resourceSnapshotId       被审批计划引用的资源快照
 * @param datasetContentFingerprint 被审批的数据集内容指纹
 * @param decision                 审批动作
 * @param operatorId               审批人 ID，来自认证上下文
 * @param decidedAt                审批时间
 * @param comment                  审批意见；退回或拒绝时必填
 */
public record PlanApproval(
        String approvalId,
        String activityId,
        String planId,
        int planVersion,
        String resourceSnapshotId,
        String datasetContentFingerprint,
        ApprovalDecision decision,
        Long operatorId,
        Instant decidedAt,
        String comment
) {

    public PlanApproval {
        approvalId = text(approvalId, "approvalId");
        activityId = text(activityId, "activityId");
        planId = text(planId, "planId");
        positive(planVersion, "planVersion");

        resourceSnapshotId = text(
                resourceSnapshotId, "resourceSnapshotId"
        );
        datasetContentFingerprint = text(
                datasetContentFingerprint, "datasetContentFingerprint"
        );

        required(decision, "decision");
        positiveId(operatorId, "operatorId");
        required(decidedAt, "decidedAt");
        comment = comment == null ? "" : comment.strip();

        if (decision != ApprovalDecision.APPROVE && comment.isEmpty()) {
            throw new IllegalArgumentException("退回或拒绝时必须填写审批意见");
        }
    }

    /**
     * 判断审批针对的是否为指定计划及其资源版本。
     *
     * <p>匹配不代表批准，还要检查 decision。</p>
     */
    public boolean matches(EvaluationPlan plan) {
        return plan != null
                && activityId.equals(plan.activityId())
                && planId.equals(plan.planId())
                && planVersion == plan.version()
                && resourceSnapshotId.equals(plan.resourceSnapshotId())
                && datasetContentFingerprint.equals(
                plan.datasetContentFingerprint()
        );
    }
}