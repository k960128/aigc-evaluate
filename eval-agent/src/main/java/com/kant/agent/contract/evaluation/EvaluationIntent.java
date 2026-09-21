package com.kant.agent.contract.evaluation;

import com.kant.agent.contract.common.ExecutionBudget;

import java.util.Set;

/**
 * 用户的评测意图。
 *
 * <p>描述本次希望评测什么、评测哪个模型、
 * 允许覆盖哪些风险，以及可以使用哪些数据集和预算。</p>
 *
 * <p>后续主智能体根据本对象提出评测计划。
 * 计划中的目标模型、风险范围、数据集和预算必须接受 Java 代码校验。</p>
 *
 * <p>本对象由后端业务服务根据用户输入和权限查询结果构建。
 * 构造函数只执行基础数据校验，不代表资源存在性和权限校验已经完成。</p>
 *
 * <p>M1 使用已有数据集。模型从允许的数据集中选择一个，
 * 不在此阶段生成新样本。</p>
 *
 * @param objective             用户希望完成的评测目标
 * @param targetModelId         本次被测模型 ID，引用现有模型配置
 * @param allowedRiskDetailsIds 本次允许评测的风险小类 ID 集合，
 *                              引用现有风险分类，不在智能体模块重复定义风险体系
 * @param allowedDatasetIds     本次允许选择的已有数据集 ID 集合
 * @param budget                本次活动的预算上限
 */
public record EvaluationIntent(
        String objective,
        Long targetModelId,
        Set<Long> allowedRiskDetailsIds,
        Set<Long> allowedDatasetIds,
        ExecutionBudget budget
) {

    /**
     * 校验意图中的基础字段，并保存集合的不可变副本。
     */
    public EvaluationIntent {
        if (objective == null || objective.isBlank()) {
            throw new IllegalArgumentException("objective 不能为空");
        }
        objective = objective.strip();

        if (targetModelId == null || targetModelId <= 0) {
            throw new IllegalArgumentException("targetModelId 必须为正整数");
        }

        allowedRiskDetailsIds = copyPositiveIds(
                allowedRiskDetailsIds,
                "allowedRiskDetailsIds"
        );

        allowedDatasetIds = copyPositiveIds(
                allowedDatasetIds,
                "allowedDatasetIds"
        );

        if (budget == null) {
            throw new IllegalArgumentException("budget 不能为空");
        }
    }

    /**
     * 校验 ID 集合并创建不可变副本。
     *
     * <p>避免调用方在对象创建后修改原始集合，
     * 导致已确定的风险范围或数据集范围发生变化。</p>
     *
     * @param ids       待校验的 ID 集合
     * @param fieldName 字段名称
     * @return 不可变的 ID 集合
     */
    private static Set<Long> copyPositiveIds(
            Set<Long> ids,
            String fieldName
    ) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }

        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException(
                        fieldName + " 中的 ID 必须为正整数"
                );
            }
        }

        return Set.copyOf(ids);
    }
}