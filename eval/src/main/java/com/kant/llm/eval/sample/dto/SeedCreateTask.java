package com.kant.llm.eval.sample.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 种子创建任务
 * @param taskName 任务名称
 * @param riskId 风险ID
 * @param riskName 风险名称
 * @param seed 种子
 * @param mutationTactics 变异策略
 * @param maxReflectionCount 反思最大次数
 * @param maxGenerationCount 生成数量
 * @param maxTokenCount 最大token数
 * @param temperature 模型温度
 */
public record SeedCreateTask(
        @JsonPropertyDescription("任务名称")
        String taskName,
        @JsonPropertyDescription("风险ID")
        Long riskId,
        @JsonPropertyDescription("风险名称")
        String riskName,
        @JsonPropertyDescription("核心恶意意图")
        String coreIntent,
        @JsonPropertyDescription("种子")
        String seed,
        @JsonPropertyDescription("变异策略")
        List<MutationTactics> mutationTactics,
        @JsonPropertyDescription("反思最大次数")
        int maxReflectionCount,
        @JsonPropertyDescription("生成数量")
        int maxGenerationCount,
        @JsonPropertyDescription("最大token数")
        int maxTokenCount,
        @JsonPropertyDescription("模型温度")
        double temperature
) {
}

/**
 * 变异战术
 * @param mutationTacticsCode 变异战术编码
 * @param mutationTacticsName 变异战术名称
 */
record MutationTactics(
        @JsonPropertyDescription("变异战术ID")
        Long mutationTacticsId,
        @JsonPropertyDescription("变异战术编码")
        String mutationTacticsCode,
        @JsonPropertyDescription("变异战术名称")
        String mutationTacticsName
) {
}