package com.kant.llm.eval.simple.controller;

import com.kant.agent.simple.metrics.BaselineRepository;
import com.kant.agent.simple.metrics.BaselineSnapshot;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 样本生产指标的基线采集入口。
 *
 * <p>用途：在调整 Agent 策略（加记忆、改编码方式、调阈值）前后各跑一批 ticket，
 * 分别采集一次快照，用两组数字说明改动到底有没有效果，而不是凭感觉判断。</p>
 *
 * <p>典型用法：</p>
 * <ol>
 *   <li>跑一批 ticket（建议 10~20 个，太少噪声大）；</li>
 *   <li>调用 {@code POST /sample-factory/baseline/snapshot?label=p0-p1_baseline}；</li>
 *   <li>改完策略后，重启进程再跑一批，用新的 label 再采一次；</li>
 *   <li>通过 {@code GET /latest} 或 {@code GET /list} 取出两份快照做对比。</li>
 * </ol>
 *
 * <p><b>重要限制</b>：快照读的是 Micrometer 的累计计数，即进程启动以来的总量，不是
 * "上一批"的区间值。因此同一进程内连续采两次，第二份快照必然包含第一批。想要严格的
 * 批次对比，必须在每批之前重启进程；或者在 Prometheus 侧用 {@code increase()} 自行
 * 计算区间增量。</p>
 */
@RestController
@RequestMapping("/sample-factory/baseline")
@RequiredArgsConstructor
public class BaselineController {

    /** Spring Boot 注入的主注册表（composite），find 会代理到具体的 Prometheus 子注册表。 */
    private final MeterRegistry registry;

    /** 快照存储，当前是内存 + 本地 JSON 文件的简化实现。 */
    private final BaselineRepository baselineRepository;

    /**
     * 采集一份当前指标快照，并以指定标签持久化。
     *
     * <p>标签建议带上阶段语义（如 before_memory、after_memory），方便后续按标签取最新一份。</p>
     *
     * @param label 快照标签，不能为空
     * @return 本次采集到的快照内容
     */
    @PostMapping("/snapshot")
    public Result<BaselineSnapshot> snapshot(@RequestParam String label) {
        // 注意：counter 是进程启动以来的累计值，不是"这一批"的区间值。
        long passed = countOf("ai.ticket.outcome", "outcome", "passed");
        long degraded = countOf("ai.ticket.outcome", "outcome", "degraded");
        long error = countOf("ai.ticket.outcome", "outcome", "error");
        // error 表示执行异常，同样属于"没拿到合格样本"；并入 failed 才能保证通过率分母完整。
        long failed = countOf("ai.ticket.outcome", "outcome", "failed") + error;
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
        // 单独暴露异常数，便于区分"质量挖不出来"和"链路执行失败"两类问题。
        extra.put("errorTickets", (double) error);

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
        return Results.success(snap);
    }

    /**
     * 查询全部历史快照，按创建时间升序返回。
     *
     * @return 快照列表，无数据时为空列表
     */
    @GetMapping("/list")
    public List<BaselineSnapshot> list() {
        return baselineRepository.findAll();
    }

    /**
     * 按标签查询最新的一份快照。
     *
     * @param label 快照标签
     * @return 最新快照；不存在时返回 null
     */
    @GetMapping("/latest")
    public BaselineSnapshot latest(@RequestParam String label) {
        return baselineRepository.latestByLabel(label).orElse(null);
    }

    // ═══════════════════════════════════════════════════════════
    // Micrometer 读数辅助
    // ═══════════════════════════════════════════════════════════

    /**
     * 统计指定指标名（可按单个标签过滤）的 counter 累计值。
     *
     * <p>同名 counter 可能因为标签不同存在多个实例，这里全部求和；不传标签时统计
     * 该名字下的所有实例。</p>
     *
     * @param name     指标名，例如 ai.ticket.outcome
     * @param tagKey   标签键；为 null 表示不过滤
     * @param tagValue 标签值；仅当 tagKey 非 null 时生效
     * @return 累计计数
     */
    private long countOf(String name, String tagKey, String tagValue) {
        var search = tagKey == null
                ? registry.find(name)
                : registry.find(name).tag(tagKey, tagValue);
        return (long) search.counters().stream().mapToDouble(Counter::count).sum();
    }

    /**
     * 统计指定指标名下所有 summary 的均值，再对结果取平均。
     *
     * @param name 指标名，例如 ai.ticket.iterations
     * @return 平均后的均值；没有数据时返回 0
     */
    private double summaryMean(String name) {
        return registry.find(name).summaries().stream()
                .mapToDouble(DistributionSummary::mean).average().orElse(0);
    }

    /**
     * 统计指定指标名 + 标签下所有 summary 的均值，再对结果取平均。
     *
     * @param name     指标名，例如 ai.judge.intent_score
     * @param tagKey   标签键
     * @param tagValue 标签值
     * @return 平均后的均值；没有数据时返回 0
     */
    private double summaryMean(String name, String tagKey, String tagValue) {
        return registry.find(name).tag(tagKey, tagValue).summaries().stream()
                .mapToDouble(DistributionSummary::mean).average().orElse(0);
    }

    /**
     * 统计指定 Timer 的平均耗时。
     *
     * @param name 指标名，例如 ai.ticket.duration
     * @return 平均耗时，单位毫秒；没有数据时返回 0
     */
    private double timerMeanMs(String name) {
        return registry.find(name).timers().stream()
                .mapToDouble(timer -> timer.mean(TimeUnit.MILLISECONDS))
                .average().orElse(0);
    }
}
