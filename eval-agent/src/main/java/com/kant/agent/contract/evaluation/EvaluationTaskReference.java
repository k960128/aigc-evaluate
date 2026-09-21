package com.kant.agent.contract.evaluation;

import static com.kant.agent.contract.common.ContractChecks.positiveId;

/**
 * 现有评测任务和指定执行批次的引用。
 *
 * @param evalTaskId    现有评测任务 ID
 * @param taskDetailId  本次提交实际产生的执行批次 ID
 * @param datasetId     本批次使用的数据集 ID
 * @param targetModelId 本批次使用的被测模型 ID
 */
public record EvaluationTaskReference(
        Long evalTaskId,
        Long taskDetailId,
        Long datasetId,
        Long targetModelId
) {

    public EvaluationTaskReference {
        positiveId(evalTaskId, "evalTaskId");
        positiveId(taskDetailId, "taskDetailId");
        positiveId(datasetId, "datasetId");
        positiveId(targetModelId, "targetModelId");
    }
}