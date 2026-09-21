package com.kant.agent.contract.evaluation;

import static com.kant.agent.contract.common.ContractChecks.*;

/**
 * 提交给现有评测系统的业务命令。
 *
 * <p>由后端根据已批准且再次校验通过的计划构建，
 * 不作为前端可以自由填写的请求对象。</p>
 *
 * @param idempotencyKey            同一次业务提交的稳定幂等键
 * @param activityId                活动 ID
 * @param planId                    计划 ID
 * @param planVersion               已批准的计划版本
 * @param targetModelId             被测模型 ID
 * @param datasetId                 正式数据集 ID
 * @param datasetContentFingerprint 已批准并重新检查的数据集指纹
 * @param plannedSampleCount        本次允许执行的样本数
 * @param maxTargetCalls            目标模型调用上限，包含重试
 * @param approvalId                本次提交依据的人工审批记录 ID
 */
public record EvaluationSubmissionCommand(
        String idempotencyKey,
        String activityId,
        String planId,
        int planVersion,
        Long targetModelId,
        Long datasetId,
        String datasetContentFingerprint,
        int plannedSampleCount,
        int maxTargetCalls,
        String approvalId
) {

    public EvaluationSubmissionCommand {
        idempotencyKey = text(idempotencyKey, "idempotencyKey");
        activityId = text(activityId, "activityId");
        planId = text(planId, "planId");
        positive(planVersion, "planVersion");
        positiveId(targetModelId, "targetModelId");
        positiveId(datasetId, "datasetId");

        datasetContentFingerprint = text(
                datasetContentFingerprint, "datasetContentFingerprint"
        );

        positive(plannedSampleCount, "plannedSampleCount");
        positive(maxTargetCalls, "maxTargetCalls");
        approvalId = text(approvalId, "approvalId");

        if (plannedSampleCount > maxTargetCalls) {
            throw new IllegalArgumentException("目标调用预算不足");
        }
    }
}