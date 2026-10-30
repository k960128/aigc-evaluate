package com.kant.llm.eval.simple.controller;

import com.kant.agent.simple.metrics.BaselineRepository;
import com.kant.agent.simple.metrics.BaselineSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Baseline 采集入口。
 * 用法：
 *   1. 跑一批 ticket（10~20 个）
 *   2. 调 POST /baseline/snapshot?label=p0-p1_baseline
 *   3. 后续 ③④ 完成后，同样方式再采一次，直接对比
 */
@RestController
@RequestMapping("/sample-factory/baseline")
@RequiredArgsConstructor
public class BaselineController {

    private final MeterRegistry registry;
    private final BaselineRepository baselineRepository;

    @PostMapping("/snapshot")
    public BaselineSnapshot snapshot(@RequestParam String label) {
        // 从 Micrometer 读取累计值，构造快照
        long passed = countOf("ai.ticket.outcome", "outcome", "passed");
        long degraded = countOf("ai.ticket.outcome", "outcome", "degraded");
        long failed = countOf("ai.ticket.outcome", "outcome", "failed");
        long total = passed + degraded + failed;

        long intentEmpty = countOf("ai.l1.intent_empty", null, null);
        long adviceFiltered = countOf("ai.judge.advice_filtered", null, null);
        long intentPreservedR1 = countOf("ai.generator.intent_preserved", "round", "1");

        double avgIterations = summaryMean("ai.ticket.iterations");
        double avgDuration = timerMeanMs("ai.ticket.duration");

        Map<String, Double> extra = new HashMap<>();
        extra.put("avgIntentScoreR1", summaryMean("ai.judge.intent_score", "round", "1"));
        extra.put("avgIntentScoreR2", summaryMean("ai.judge.intent_score", "round", "2"));
        extra.put("avgIntentScoreR3", summaryMean("ai.judge.intent_score", "round", "3"));
        extra.put("avgStealthScoreR1", summaryMean("ai.judge.stealth_score", "round", "1"));
        extra.put("avgBestSoFarComposite", summaryMean("ai.best_so_far.composite"));

        BaselineSnapshot snap = new BaselineSnapshot(
                UUID.randomUUID().toString(),
                Instant.now(),
                label,
                (int) total,
                (int) passed,
                (int) degraded,
                (int) failed,
                total == 0 ? 0 : (double) passed / total,
                total == 0 ? 0 : (double) intentPreservedR1 / total,
                total == 0 ? 0 : (double) intentEmpty / total,
                total == 0 ? 0 : (double) adviceFiltered / total,
                avgIterations,
                avgDuration,
                extra
        );
        baselineRepository.save(snap);
        return snap;
    }

    @GetMapping("/list")
    public List<BaselineSnapshot> list() {
        return baselineRepository.findAll();
    }

    @GetMapping("/latest")
    public BaselineSnapshot latest(@RequestParam String label) {
        return baselineRepository.latestByLabel(label).orElse(null);
    }

    // ─── Micrometer 读数辅助 ───

    private long countOf(String name, String tagKey, String tagValue) {
        var search = tagKey == null
                ? registry.find(name)
                : registry.find(name).tag(tagKey, tagValue);
        return (long) search.counters().stream().mapToDouble(Counter::count).sum();
    }
    private double summaryMean(String name) {
        return registry.find(name).summaries().stream()
                .mapToDouble(DistributionSummary::mean).average().orElse(0);
    }

    private double summaryMean(String name, String tagKey, String tagValue) {
        return registry.find(name).tag(tagKey, tagValue).summaries().stream()
                .mapToDouble(DistributionSummary::mean).average().orElse(0);
    }

    private double timerMeanMs(String name) {
        return registry.find(name).timers().stream()
                .mapToDouble(t -> t.mean(TimeUnit.MILLISECONDS))
                .average().orElse(0);
    }
}
