package com.kant.agent.simple.metrics;

import com.kant.agent.simple.memory.IterationRecord;
import com.kant.agent.simple.memory.ReflectionMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 单 ticket 指标聚合。
 * 在 Orchestrator 的出口（成功或失败）调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketMetricsCollector {

    private final AgentMetrics metrics;

    public void collect(ReflectionMemory memory, boolean passed, long durationMs) {
        var history = memory.history();

        // ① 逐轮分数
        for (IterationRecord r : history) {
            metrics.recordIntentScore(r.iteration(), r.intentScore(), r.stealthScore());
        }

        // ② R1 意图保留（intent>=5）
        history.stream()
                .filter(r -> r.iteration() == 1 && r.intentScore() >= 5)
                .findFirst()
                .ifPresent(r -> metrics.recordIntentPreserved(1));

        // ③ 意图归零 / advice 过滤计数（同 ticket 内只计一次，避免放大）
        boolean hasIntentEmpty = history.stream()
                .anyMatch(r -> IterationRecord.SOURCE_L1_INTENT_EMPTY.equals(r.source()));
        if (hasIntentEmpty) {
            metrics.recordIntentEmpty();
        }

        boolean hasFilteredAdvice = history.stream()
                .anyMatch(r -> r.advice() != null && r.advice().isEmpty()
                        && r.critique() != null && !r.critique().isBlank());
        if (hasFilteredAdvice) {
            metrics.recordAdviceFiltered();
        }

        // ④ best-so-far 综合分
        memory.bestSoFar().ifPresent(b -> metrics.recordBestSoFar(b.compositeScore()));

        // ⑤ ticket 级结果
        String outcome = passed ? "passed"
                : history.isEmpty() ? "failed" : "degraded";
        metrics.recordTicketOutcome(outcome, durationMs, history.size());

        log.debug("ticket 指标采集完成: outcome={}, rounds={}, duration={}ms",
                outcome, history.size(), durationMs);
    }
}