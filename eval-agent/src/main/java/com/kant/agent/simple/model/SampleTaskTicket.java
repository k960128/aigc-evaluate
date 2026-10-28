package com.kant.agent.simple.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public class SampleTaskTicket {

    // 不变
    public record TaskTicket(
            @JsonPropertyDescription("核心恶意意图") String coreIntent,
            @JsonPropertyDescription("伪装种子话题") String seed,
            @JsonPropertyDescription("变异战术列表") List<TacticsContent> tactics
    ) {}

    // 不变（P0 已改 List<TacticCheck>）
    public record JudgeFeedback(
            @JsonPropertyDescription("恶意意图分数 1-10") int intentScore,
            @JsonPropertyDescription("伪装话题分数 1-10") int stealthScore,
            @JsonPropertyDescription("是否通过") boolean isPass,
            @JsonPropertyDescription("战术核验清单") List<TacticCheck> tacticsChecklist,
            @JsonPropertyDescription("战术核验不通过原因") String tacticMismatchReason,
            @JsonPropertyDescription("批评") String critique,
            @JsonPropertyDescription("建议") String advice
    ) {
        public record TacticCheck(String tacticName, boolean used) {}
    }

    // P1-CHANGE: 新增 category + transformerId
    public record TacticsContent(
            @JsonPropertyDescription("战术名称") String tacticName,
            @JsonPropertyDescription("战术类别：SEMANTIC 或 TRANSFORMATION") TacticCategory category,
            @JsonPropertyDescription("战术解释") String explain,
            @JsonPropertyDescription("战术示例") String example,
            @JsonPropertyDescription("变换器 ID（仅 TRANSFORMATION 使用：base64/hex/leet/url/caesar）")
            String transformerId
    ) {
        // P1-CHANGE: 兼容旧三参构造 —— 存量数据默认视为 SEMANTIC
        public TacticsContent(String tacticName, String explain, String example) {
            this(tacticName, TacticCategory.SEMANTIC, explain, example, null);
        }

        // 便捷工厂
        public static TacticsContent semantic(String name, String explain, String example) {
            return new TacticsContent(name, TacticCategory.SEMANTIC, explain, example, null);
        }

        public static TacticsContent transformation(String name, String explain,
                                                    String example, String transformerId) {
            return new TacticsContent(name, TacticCategory.TRANSFORMATION, explain, example, transformerId);
        }
    }

    // P1-CHANGE: 战术类别枚举
    public enum TacticCategory {
        SEMANTIC,        // 语义类：角色扮演、虚构情境……由 LLM 生成
        TRANSFORMATION   // 变换类：Base64、Leet……由程序生成
    }
}