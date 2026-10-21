package com.kant.llm.eval.sample.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 创建样本扩充任务请求。
 *
 * @param baseTargetConf 安全基线与风险靶点配置
 * @param mutationTacticsConf 变异战术列表配置
 * @param engineParamsConf 单批迭代引擎参数配置
 */
public record SeedCreateTask(
        @NotBlank(message = "任务名称不能为空")
        @JsonPropertyDescription("任务名称")
        String taskName,
        @NotNull(message = "安全基线与风险靶点不能为空")
        @Valid
        @JsonPropertyDescription("安全基线与风险靶点")
        BaseTargetConf baseTargetConf,
        @NotEmpty(message = "变异战术列表不能为空")
        @JsonPropertyDescription("变异策略")
        List<@NotNull(message = "变异战术不能为空") @Valid MutationTacticsConf> mutationTacticsConf,
        @NotNull(message = "引擎参数不能为空")
        @Valid
        @JsonPropertyDescription("引擎参数")
        EngineParamsConf engineParamsConf
) {

    /**
     * 安全基线与风险靶点配置。
     *
     * @param riskId 风险ID
     * @param riskName 风险名称
     * @param coreIntent 核心恶意意图
     * @param seed 种子内容
     */
    public record BaseTargetConf(
            @NotNull(message = "风险ID不能为空")
            @Positive(message = "风险ID必须大于0")
            @JsonPropertyDescription("风险ID")
            Long riskId,
            @NotBlank(message = "风险名称不能为空")
            @JsonPropertyDescription("风险名称")
            String riskName,
            @NotBlank(message = "核心恶意意图不能为空")
            @JsonPropertyDescription("核心恶意意图")
            String coreIntent,
            @NotBlank(message = "种子内容不能为空")
            @JsonPropertyDescription("种子")
            String seed
    ) {
    }

    /**
     * 变异战术配置。
     *
     * @param mutationTacticsId 变异战术ID
     * @param mutationTacticsCode 变异战术编码
     * @param mutationTacticsName 变异战术名称
     */
    public record MutationTacticsConf(
            @NotNull(message = "变异战术ID不能为空")
            @Positive(message = "变异战术ID必须大于0")
            @JsonPropertyDescription("变异战术ID")
            Long mutationTacticsId,
            @NotBlank(message = "变异战术编码不能为空")
            @JsonPropertyDescription("变异战术编码")
            String mutationTacticsCode,
            @NotBlank(message = "变异战术名称不能为空")
            @JsonPropertyDescription("变异战术名称")
            String mutationTacticsName
    ) {
    }

    /**
     * 单批迭代引擎参数配置。
     *
     * @param maxReflectionCount 反思最大次数，允许为0
     * @param maxGenerationCount 生成数量，必须大于0
     * @param maxTokenCount 最大Token数，必须大于0
     * @param temperature 模型温度，范围为0到2，包含边界值
     */
    public record EngineParamsConf(
            @NotNull(message = "反思最大次数不能为空")
            @Min(value = 0, message = "反思最大次数不能小于0")
            @JsonPropertyDescription("反思最大次数")
            Integer maxReflectionCount,
            @NotNull(message = "生成数量不能为空")
            @Positive(message = "生成数量必须大于0")
            @JsonPropertyDescription("生成数量")
            Integer maxGenerationCount,
            @NotNull(message = "最大Token数不能为空")
            @Positive(message = "最大Token数必须大于0")
            @JsonPropertyDescription("最大token数")
            Integer maxTokenCount,
            @NotNull(message = "模型温度不能为空")
            @DecimalMin(value = "0.0", message = "模型温度不能小于0")
            @DecimalMax(value = "1.0", message = "模型温度不能大于1")
            @JsonPropertyDescription("模型温度")
            Double temperature
    ) {
    }
}
