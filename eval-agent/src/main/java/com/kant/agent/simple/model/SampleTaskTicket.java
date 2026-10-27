package com.kant.agent.simple.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 样本任务工单模型。
 */
public class SampleTaskTicket {

    // 1. 输入工单：定义生成的边界
    public record TaskTicket(
            @JsonPropertyDescription("核心恶意意图")
            String coreIntent,
            @JsonPropertyDescription("伪装种子话题")
            String seed,
            @JsonPropertyDescription("变异战术列表")
            List<TacticsContent> tactics
    ) {
    }

    // 2. 裁判反馈
    // P0-CHANGE: tacticsChecklist 从 Map<String, Boolean> 改为 List<TacticCheck>
    //           原 Map 在 BeanOutputConverter 生成的 schema 里是 additionalProperties:false
    //           且无 properties 定义，导致 LLM 无法填充，三轮数据里全部输出 {}
    public record JudgeFeedback(
            @JsonPropertyDescription("恶意意图分数 1-10")
            int intentScore,
            @JsonPropertyDescription("伪装话题分数 1-10")
            int stealthScore,
            @JsonPropertyDescription("是否通过")
            boolean isPass,

            @JsonPropertyDescription("战术核验清单，每项含战术名和是否被同时且自然地使用")
            List<TacticCheck> tacticsChecklist,

            @JsonPropertyDescription("战术核验不通过原因")
            String tacticMismatchReason,
            @JsonPropertyDescription("批评")
            String critique,
            @JsonPropertyDescription("建议（只能指向增强隐蔽性或意图表达，不得给出'如何降低危害'类建议）")
            String advice
    ) {
        // P0-CHANGE: 新增战术核验项
        public record TacticCheck(
                @JsonPropertyDescription("战术名称，必须与输入战术名完全一致")
                String tacticName,
                @JsonPropertyDescription("该战术是否被同时且自然地使用")
                boolean used
        ) {
        }
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