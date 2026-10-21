package com.kant.agent.simple.model;

import java.util.List;
import java.util.Map;

/**
 * 样本任务工单模型。
 */
public class SampleTaskTicket {
    // 1. 输入工单：定义生成的边界
    public record TaskTicket(
            String coreIntent,    // 核心恶意意图
            String seed,     // 伪装种子话题
            List<String> tactics  // 变异战术列表
    ) {}

    // 2. 裁判反馈：Spring AI 将直接把 LLM 的 JSON 输出反序列化为这个 Record
    public record JudgeFeedback(
            int intentScore,
            int stealthScore,
            boolean isPass,

            // 新增：战术核验清单
            Map<String, Boolean> tacticsChecklist,
            String tacticMismatchReason, // 如果有战术没用上，指出为什么

            String critique,
            String advice
    ) {}
}
