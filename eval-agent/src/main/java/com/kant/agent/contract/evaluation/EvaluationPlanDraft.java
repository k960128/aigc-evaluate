package com.kant.agent.contract.evaluation;

import java.util.Set;

/**
 * 主智能体提出的评测计划草案。
 *
 * <p>草案是模型输出的结构化业务建议，
 * 只有经过后端业务校验后，才能形成正式的版本化计划。</p>
 *
 * <p>本对象不包含审批状态、操作人或执行预算。
 * 智能体不能通过返回字段给自己授权，也不能扩大用户预算。</p>
 *
 * <p>构造函数只进行基础字段校验。
 * 目标模型是否一致、数据集是否允许使用、风险范围是否越界，
 * 由后续 EvaluationPlanValidator 检查。</p>
 *
 * @param targetModelId  计划使用的被测模型 ID
 * @param datasetId      计划选择的已有数据集 ID
 * @param riskDetailsIds 计划希望覆盖的风险小类 ID 集合
 * @param objective      本次计划的具体评测目标
 * @param rationale      选择该方案的简短业务依据，用于人工理解和审批
 */
public record EvaluationPlanDraft(
        Long targetModelId,
        Long datasetId,
        Set<Long> riskDetailsIds,
        String objective,
        String rationale
) {

    public EvaluationPlanDraft {
        requirePositiveId(targetModelId, "targetModelId");
        requirePositiveId(datasetId, "datasetId");

        if (riskDetailsIds == null || riskDetailsIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "riskDetailsIds 不能为空"
            );
        }

        for (Long riskId : riskDetailsIds) {
            requirePositiveId(riskId, "riskDetailsIds 中的 ID");
        }

        riskDetailsIds = Set.copyOf(riskDetailsIds);

        objective = requireText(objective, "objective");
        rationale = requireText(rationale, "rationale");
    }

    /**
     * 校验资源 ID 必须为正整数。
     */
    private static void requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " 必须为正整数"
            );
        }
    }

    /**
     * 校验必填文本。
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " 不能为空"
            );
        }

        return value.strip();
    }
}