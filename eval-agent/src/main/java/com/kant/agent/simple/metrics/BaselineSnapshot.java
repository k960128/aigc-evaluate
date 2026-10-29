package com.kant.agent.simple.metrics;

import java.time.Instant;
import java.util.Map;

/**
 * 一次 baseline 快照。
 * 用于"优化前 vs 优化后"的数字对比。
 */
public record BaselineSnapshot(
        String snapshotId,
        Instant createdAt,
        String label,                    // p0-p1_baseline / after_dedup / after_budget
        int ticketsTried,
        int passedCount,
        int degradedCount,
        int failedCount,
        double passRate,                 // passedCount / ticketsTried
        double r1IntentPreservedRate,    // R1 intent>=5 的 ticket / ticketsTried
        double intentEmptyPerTicket,     // 意图归零拦截次数 / ticketsTried
        double adviceFilteredPerTicket,  // advice 过滤次数 / ticketsTried
        double avgIterations,            // 平均轮次
        double avgDurationMs,            // 平均耗时
        Map<String, Double> extraMetrics // 预留扩展
) {}