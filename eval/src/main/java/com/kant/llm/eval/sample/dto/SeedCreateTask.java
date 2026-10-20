package com.kant.llm.eval.sample.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record SeedCreateTask(
        @JsonPropertyDescription("安全基线与风险靶点")
        BaseTarget baseTarget,
        @JsonPropertyDescription("变异策略")
        List<MutationTactics> mutationTactics,
        @JsonPropertyDescription("引擎参数")
        EngineParams engineParams
) {
}

/**
 * 安全基线与风险靶点
 * @param taskName 任务名称
 * @param riskId 风险ID
 * @param riskName 风险名称
 * @param coreIntent 核心恶意意图
 * @param seed 种子
 */
record BaseTarget(@JsonPropertyDescription("任务名称")
                  String taskName,
                  @JsonPropertyDescription("风险ID")
                  Long riskId,
                  @JsonPropertyDescription("风险名称")
                  String riskName,
                  @JsonPropertyDescription("核心恶意意图")
                  String coreIntent,
                  @JsonPropertyDescription("种子")
                  String seed) {
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

/**
 * 种子创建任务参数
 * @param maxReflectionCount 反思最大次数
 * @param maxGenerationCount 生成数量
 * @param maxTokenCount 最大token数
 * @param temperature 模型温度
 */
record EngineParams(@JsonPropertyDescription("反思最大次数")
                    int maxReflectionCount,
                    @JsonPropertyDescription("生成数量")
                    int maxGenerationCount,
                    @JsonPropertyDescription("最大token数")
                    int maxTokenCount,
                    @JsonPropertyDescription("模型温度")
                    double temperature) {
}