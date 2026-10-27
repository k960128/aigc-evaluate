package com.kant.agent.simple.memory;

/**
 * 一次迭代的完整快照。
 *
 * 设计要点：
 * - scaffold 存"骨架"，不存最终 finalText（编码对 LLM 是噪声）
 * - source 区分失败来源，便于 Orchestrator 做"连续同类失败"判断
 * - compositeScore 权重写死在此处，便于集中调参
 */
public record IterationRecord(
        int iteration,
        String scaffold,
        int intentScore,
        int stealthScore,
        boolean passed,
        String critique,
        String advice,
        String source
) {
    public static final String SOURCE_GENERATOR = "GENERATOR";
    public static final String SOURCE_L1_INTENT_EMPTY = "L1_INTENT_EMPTY";
    public static final String SOURCE_GATE_DUP = "GATE_DUP";

    /** 综合分：intent 权重 6，stealth 权重 4 */
    public int compositeScore() {
        return intentScore * 6 + stealthScore * 4;
    }

    /** L1 意图归零拦截时的构造 */
    public static IterationRecord intentEmpty(int iteration, String scaffold) {
        return new IterationRecord(iteration, scaffold, 0, 0, false,
                "骨架完全不含核心意图相关词", "", SOURCE_L1_INTENT_EMPTY);
    }

    /** 向量去重打回时的构造（P1 ③ 完成后使用） */
    public static IterationRecord duplicate(int iteration, String scaffold, String topScaffold) {
        return new IterationRecord(iteration, scaffold, 0, 0, false,
                "与历史样本高度相似：" + topScaffold, "", SOURCE_GATE_DUP);
    }
}