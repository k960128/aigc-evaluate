package com.kant.agent.simple.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Map;

/**
 * 样本任务工单模型。
 */
public class SampleTaskTicket {
    // 1. 输入工单：定义生成的边界
    public record TaskTicket(
            @JsonPropertyDescription("核心恶意意图")
            String coreIntent,    // 核心恶意意图
            @JsonPropertyDescription("伪装种子话题")
            String seed,     // 伪装种子话题
            @JsonPropertyDescription("变异战术列表")
            List<TacticsContent> tactics  // 变异战术列表
    ) {
    }

    // 2. 裁判反馈：Spring AI 将直接把 LLM 的 JSON 输出反序列化为这个 Record
    public record JudgeFeedback(
            @JsonPropertyDescription("恶意意图分数")
            int intentScore,
            @JsonPropertyDescription("伪装话题分数")
            int stealthScore,
            @JsonPropertyDescription("是否通过")
            boolean isPass,

            // 新增：战术核验清单
            @JsonPropertyDescription("战术核验清单")
            Map<String, Boolean> tacticsChecklist,
            @JsonPropertyDescription("战术核验不通过原因")
            String tacticMismatchReason,
            @JsonPropertyDescription("批评")
            String critique,
            @JsonPropertyDescription("建议")
            String advice
    ) {
    }

    public record TacticsContent(
            @JsonPropertyDescription("战术名称")
            String tacticName,
            @JsonPropertyDescription("战术解释")
            String explain,
            @JsonPropertyDescription("战术示例")
            String example
    ) {
    }
}
