package com.kant.agent.contract.evaluation;

import com.kant.agent.contract.common.ExecutionBudget;

import java.time.Instant;

import static com.kant.agent.contract.common.ContractChecks.*;

/**
 * 由后端管理的版本化评测计划。
 *
 * <p>模型只产生 draft。
 * 其余身份、预算、快照和追踪字段由后端提供。</p>
 *
 * @param planId                   计划唯一标识
 * @param activityId               所属评测活动 ID
 * @param version                  计划版本，从 1 开始
 * @param draft                    经过业务校验的计划草案
 * @param budget                   本版计划使用的授权预算
 * @param resourceSnapshotId       制定计划时使用的资源快照 ID
 * @param datasetContentFingerprint 所选数据集当时的内容指纹
 * @param plannedSampleCount       按当时数据集内容确定的执行样本数
 * @param createdAt                计划创建时间
 * @param agentRunId               产生该计划草案的智能体执行 ID
 */
public record EvaluationPlan(
        String planId,
        String activityId,
        int version,
        EvaluationPlanDraft draft,
        ExecutionBudget budget,
        String resourceSnapshotId,
        String datasetContentFingerprint,
        int plannedSampleCount,
        Instant createdAt,
        String agentRunId
) {

    public EvaluationPlan {
        planId = text(planId, "planId");
        activityId = text(activityId, "activityId");
        positive(version, "version");
        required(draft, "draft");
        required(budget, "budget");

        resourceSnapshotId = text(
                resourceSnapshotId, "resourceSnapshotId"
        );
        datasetContentFingerprint = text(
                datasetContentFingerprint, "datasetContentFingerprint"
        );

        positive(plannedSampleCount, "plannedSampleCount");
        required(createdAt, "createdAt");
        agentRunId = text(agentRunId, "agentRunId");

        if (plannedSampleCount > budget.maxEvaluationSamples()) {
            throw new IllegalArgumentException("计划样本数超过样本预算");
        }

        // M1 每条样本至少需要调用一次目标模型。
        // 重试是否还有预算，由执行过程另行控制。
        if (plannedSampleCount > budget.maxTargetCalls()) {
            throw new IllegalArgumentException("计划样本数超过目标调用预算");
        }
    }
}