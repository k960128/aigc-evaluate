package com.kant.agent.simple.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 自定义业务指标埋点。
 *
 * 命名约定：
 * - ai.ticket.*      单 ticket 级
 * - ai.judge.*       评审分数
 * - ai.l1.*          程序化拦截
 * - ai.best_so_far.* 降级池
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ─── ticket 级 ───

    public void recordTicketOutcome(String outcome, long durationMs, int iterations) {
        Counter.builder("ai.ticket.outcome")
                .description("Ticket 最终结果")
                .tag("outcome", outcome)     // passed / degraded / failed
                .register(registry).increment();

        Timer.builder("ai.ticket.duration")
                .description("Ticket 全流程耗时")
                .publishPercentileHistogram()
                .register(registry).record(durationMs, TimeUnit.MILLISECONDS);

        DistributionSummary.builder("ai.ticket.iterations")
                .description("Ticket 实际迭代轮次")
                .register(registry).record(iterations);
    }

    // ─── 轮次级 ───

    public void recordIntentScore(int round, int intent, int stealth) {
        DistributionSummary.builder("ai.judge.intent_score")
                .description("Judge 给的 intent 分数")
                .tag("round", String.valueOf(round))
                .publishPercentileHistogram()
                .register(registry).record(intent);

        DistributionSummary.builder("ai.judge.stealth_score")
                .description("Judge 给的 stealth 分数")
                .tag("round", String.valueOf(round))
                .publishPercentileHistogram()
                .register(registry).record(stealth);
    }

    public void recordIntentPreserved(int round) {
        Counter.builder("ai.generator.intent_preserved")
                .description("意图保留（intent>=5）的轮次数")
                .tag("round", String.valueOf(round))
                .register(registry).increment();
    }

    // ─── 程序化拦截 ───

    public void recordIntentEmpty() {
        Counter.builder("ai.l1.intent_empty")
                .description("L1 意图归零拦截次数")
                .register(registry).increment();
    }

    public void recordAdviceFiltered() {
        Counter.builder("ai.judge.advice_filtered")
                .description("Judge 安全对齐 advice 被过滤次数")
                .register(registry).increment();
    }

    // ─── 降级池 ───

    public void recordBestSoFar(double composite) {
        DistributionSummary.builder("ai.best_so_far.composite")
                .description("3 轮失败时 best-so-far 的综合分")
                .register(registry).record(composite);
    }

    // ─── Spring AI 内置指标说明 ───
    // 以下指标由 Spring AI 1.1.0 自动上报，无需手动埋点：
    //   gen_ai.client.token.usage{gen_ai.token.type=input/output/total}
    //   gen_ai.client.operation{gen_ai.provider.name, gen_ai.request.model}
    // 在 /actuator/prometheus 中可直接查到。
}