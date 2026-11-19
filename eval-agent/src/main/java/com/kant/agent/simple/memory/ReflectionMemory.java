package com.kant.agent.simple.memory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 单 ticket 的反思记忆。
 * 生命周期：一个 runReflectionLoop 调用内。
 * 线程安全：不需要（单线程使用）。
 */
public class ReflectionMemory {

    private final List<IterationRecord> history = new ArrayList<>();
    private final BestSoFarSelector bestSelector;

    public ReflectionMemory() {
        this(new BestSoFarSelector.Default());
    }

    public ReflectionMemory(BestSoFarSelector bestSelector) {
        this.bestSelector = bestSelector;
    }

    // ─── 写入 ───

    public void record(IterationRecord record) {
        history.add(record);
    }

    // ─── 读取 ───

    public List<IterationRecord> history() {
        return List.copyOf(history);
    }

    public boolean isEmpty() {
        return history.isEmpty();
    }

    public int size() {
        return history.size();
    }

    /** 已失败的轮数（不含通过轮） */
    public int failedCount() {
        return (int) history.stream().filter(r -> !r.passed()).count();
    }

    // ─── ① 锚点：best-so-far ───

    public Optional<IterationRecord> bestSoFar() {
        return bestSelector.select(history);
    }

    // ─── ② 反模式：提炼重复出现的缺陷 ───

    /**
     * 从多轮 critique 里提炼重复出现的短语。
     *
     * 策略：按句号/逗号/分号切句 → 过滤 <6 字的短句 → 统计频率 >=2 的 → 返回。
     * 不做 NLP 聚类（7B 场景不需要那么复杂），先跑起来，观察效果再优化。
     */
    public List<String> antiPatterns() {
        return history.stream()
                .map(IterationRecord::critique)
                .filter(Objects::nonNull)
                .flatMap(c -> Arrays.stream(c.split("[，。；;,.!?]")))
                .map(String::trim)
                .filter(s -> s.length() >= 6)          // 太短的无意义
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() >= 2)         // 至少出现 2 次
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(5)                               // 最多 5 条，防 prompt 膨胀
                .toList();
    }

    // ─── ③ 连续意图归零检测 ───

    /**
     * 最近 N 轮是否全部是"意图归零"失败。
     * 供 Orchestrator 判断是否要"强制换方向"（换 seed / 换战术）。
     */
    public boolean consecutiveIntentEmpty(int threshold) {
        if (history.size() < threshold) return false;
        return history.subList(history.size() - threshold, history.size())
                .stream()
                .allMatch(r -> "L1_INTENT_EMPTY".equals(r.source()));
    }

    /**
     * 最近 N 轮是否全部是"向量去重"失败。
     * 供 Orchestrator 判断相似度是否已卡死。
     */
    public boolean consecutiveDuplicate(int threshold) {
        if (history.size() < threshold) return false;
        return history.subList(history.size() - threshold, history.size())
                .stream()
                .allMatch(r -> "GATE_DUP".equals(r.source()));
    }
}