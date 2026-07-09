package com.kant.llm.agent.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 安全评测多轮补槽结果。
 *
 * <p>该对象只用于“已有待补槽状态”的第二轮及后续轮次。
 * 模型在此阶段不能重新判断业务意图，只能基于上一轮已经确认的 intent、已有 entities 和 missingSlots，
 * 从用户补充内容中抽取新增槽位。</p>
 */
public record SecurityEvalSlotFillResult(
        @JsonPropertyDescription("补槽判断的简要理由。只说明本轮补充内容补到了哪些槽位，或为什么没有补到关键槽位")
        String reasoning,

        @JsonPropertyDescription("当前用户输入是否是在回答上一轮澄清问题。true 表示是补槽内容，false 表示未能确认是在补槽")
        boolean supplement,

        @JsonPropertyDescription("补槽抽取置信度，取值范围 0 到 1")
        Double confidence,

        @JsonPropertyDescription("本轮用户补充内容已经补齐或更新的槽位名称列表，字段名必须来自 entities")
        List<String> filledSlots,

        @JsonPropertyDescription("结合上一轮 missingSlots 后仍未补齐的槽位名称列表，字段名必须来自 entities")
        List<String> unresolvedSlots,

        @JsonPropertyDescription("从本轮用户补充内容中抽取出的安全评测实体和参数。没有补到的字段保持为空")
        SecurityEvalIntentRecognitionResult.Entities entities
) {
}
