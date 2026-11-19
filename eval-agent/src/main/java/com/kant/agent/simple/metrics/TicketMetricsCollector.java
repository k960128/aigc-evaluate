package com.kant.agent.simple.metrics;

import com.kant.agent.simple.memory.ReflectionMemory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 单 ticket 指标采集门面：把"过程中的事件"和"结束时的结果"拆成两个采集点。
 *
 * <h3>为什么拆成两个采集点</h3>
 * <ul>
 *   <li><b>事件点</b>（{@link #onRoundScored}、{@link #onIntentEmpty}、
 *       {@link #onTransformerIncomplete}、{@link #onAdviceFiltered}）：
 *       事件发生即写指标，保证监控实时可见；进程被强杀时，已经发生的事件不会丢。</li>
 *   <li><b>收口点</b>（{@link #collect}）：只在 ticket 结束时调用一次，记录最终结果、
 *       总耗时、轮次和 best-so-far。这些值必须等 ticket 结束才成立，无法提前写。</li>
 * </ul>
 *
 * <p>如果所有指标都堆在收口点，会有两个问题：一是长耗时 ticket 在跑的过程中监控上
 * 没有任何数据；二是进程异常退出时 finally 不执行，整个 ticket 的指标全部丢失，而
 * 丢的往往正好是异常样本，形成统计上的幸存者偏差。</p>
 *
 * <h3>与旧实现的差异</h3>
 * <p>旧实现在收口时回放整段迭代历史来补记逐轮分数，会把"意图归零""编码变换失败"
 * 这些没有进过 Judge 的轮次按 0 分计入评审分数，污染均值与分位数。现在逐轮分数只在
 * 真正拿到 Judge 结果时记录，收口点只负责整体结果，两类指标不再互相干扰。</p>
 *
 * <h3>线程安全</h3>
 * <p>本类被 Spring 注册为单例，但自身不保存任何 ticket 状态：所有数据都通过方法参数
 * 传入，指标直接写入 Micrometer。因此多个 ticket 并发执行时可以安全共用一个实例。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketMetricsCollector {

    /** 首轮轮次常量，用于"意图保留"事件的 round 标签。 */
    private static final int FIRST_ROUND = 1;

    /**
     * 判定"首轮意图保留"的分数门槛。
     *
     * <p>刻意低于通过线的 8：这里衡量的是"意图有没有丢"，不是"样本合不合格"。
     * 两者口径不同，如果用 8 作为门槛，绝大多数 ticket 都不会被计数，指标就失去了
     * 观察 Generator 起步质量的意义。</p>
     */
    private static final int INTENT_PRESERVED_THRESHOLD = 5;

    /**
     * 空实现：所有指标写入一个不会被导出的本地注册表。
     *
     * <p>用于单测、脚本或非 Spring 环境，让调用方不必到处判空。做成静态常量而不是
     * 每次 new，是为了避免高频构造时不断创建 MeterRegistry 和 meter。</p>
     */
    private static final TicketMetricsCollector NOOP =
            new TicketMetricsCollector(new AgentMetrics(new SimpleMeterRegistry()));

    /** 指标底层写入出口，所有 Micrometer API 都收敛在 {@link AgentMetrics} 中。 */
    private final AgentMetrics metrics;

    /**
     * 获取空实现，指标只记录在本地内存注册表里，不会导出到 Prometheus。
     *
     * <p>命名上刻意用 noop 而不是 getInstance，提醒调用方"这里不会产生可观测数据"。</p>
     *
     * @return 全局共享的空实现
     */
    public static TicketMetricsCollector noop() {
        return NOOP;
    }

    // ═══════════════════════════════════════════════════════════
    // ticket 生命周期
    // ═══════════════════════════════════════════════════════════

    /**
     * ticket 开始执行时调用，用于维护在途数量。
     *
     * <p>必须与 {@link #onTicketFinished()} 成对出现：编排器进入循环前调用本方法，
     * 在 finally 中调用结束方法，异常路径也不会让在途计数泄漏。</p>
     */
    public void onTicketStarted() {
        metrics.ticketStarted();
    }

    /**
     * ticket 结束（成功、失败或异常）时调用。
     *
     * <p>放在 finally 中执行，与 {@link #collect} 一起构成 ticket 收口动作。</p>
     */
    public void onTicketFinished() {
        metrics.ticketFinished();
    }

    // ═══════════════════════════════════════════════════════════
    // 事件点采集：发生即写，保证实时性与抗中断
    // ═══════════════════════════════════════════════════════════

    /**
     * 记录某一轮真正进入 Judge 评审后的两个维度分数。
     *
     * <p>调用时机是 Judge 返回结果之后、判断是否通过之前。只有走过完整生成链路并拿到
     * 评审结果的轮次才会调用，被程序化拦截的轮次不会进来，因此不会出现 0 分污染。</p>
     *
     * <p>首轮额外记录"意图保留"事件：首轮分数最能反映 Generator 的起步质量，
     * 后续轮次是在反思上下文加持下的结果，两者不适合混在一起看。</p>
     *
     * @param round   第几轮，从 1 开始
     * @param intent  意图保留度，1~10
     * @param stealth 伪装自然度，1~10
     */
    public void onRoundScored(int round, int intent, int stealth) {
        metrics.recordIntentScore(round, intent, stealth);
        if (round == FIRST_ROUND && intent >= INTENT_PRESERVED_THRESHOLD) {
            metrics.recordIntentPreserved(FIRST_ROUND);
        }
    }

    /**
     * 记录一次意图归零拦截。
     *
     * <p>调用时机是生成骨架之后、编码变换之前。连续出现说明模型在伪装话题里把核心
     * 意图丢了，是下一轮反思需要优先纠正的问题。</p>
     */
    public void onIntentEmpty() {
        metrics.recordIntentEmpty();
    }

    /**
     * 记录一次编码变换不完整拦截。
     *
     * <p>调用时机是程序化变换之后、Judge 之前。该指标升高通常意味着模型没有按
     * 占位符协议输出，或者战术配置引用了未注册的变换器。</p>
     */
    public void onTransformerIncomplete() {
        metrics.recordTransformerIncomplete();
    }

    /**
     * 记录一次 Judge 反馈被安全对齐话术过滤。
     *
     * <p>调用时机是拿到 Judge 的 advice 之后、写入反思记忆之前。该指标用于观察
     * 评审模型的立场漂移，偏高说明 Judge 开始给出"降低危害"类建议。</p>
     */
    public void onAdviceFiltered() {
        metrics.recordAdviceFiltered();
    }

    // ═══════════════════════════════════════════════════════════
    // 收口点采集：只有 ticket 结束时才成立的数据
    // ═══════════════════════════════════════════════════════════

    /**
     * ticket 收口采集：记录最终结果、总耗时、尝试轮次和 best-so-far。
     *
     * <p>本方法只处理 ticket 级指标，不再回放迭代历史补记逐轮分数——逐轮分数已经
     * 在 {@link #onRoundScored} 中实时记录，这里重复统计会导致重复计数。</p>
     *
     * @param memory     本次 ticket 的反思记忆，用于取 best-so-far；允许为空（空记忆）
     * @param iterations 实际尝试的轮次，包含异常中断的那一轮，由编排器统计
     * @param outcome    ticket 最终结果
     * @param durationMs ticket 总耗时，单位毫秒
     */
    public void collect(ReflectionMemory memory, int iterations, Outcome outcome, long durationMs) {
        // best-so-far 只在失败收口时才有意义：通过时不存在降级样本。
        if (memory != null) {
            memory.bestSoFar().ifPresent(best -> metrics.recordBestSoFar(best.compositeScore()));
        }
        metrics.recordTicketOutcome(outcome.tag(), durationMs, iterations);
        log.debug("ticket 指标收口完成: outcome={}({}), rounds={}, duration={}ms",
                outcome.tag(), outcome.description(), iterations, durationMs);
    }

    /**
     * ticket 最终结果分类。
     *
     * <p>把结果显式建模成枚举，而不是在采集方法里用布尔值和"历史是否为空"猜：
     * 猜出来的分类无法区分"轮次耗尽"和"执行异常"，会让失败率与降级率互相污染。</p>
     *
     * <p>标签取值直接作为 Prometheus 的 outcome 标签，修改时要注意兼容既有看板。</p>
     */
    public enum Outcome {

        /** 通过评审：在轮次上限内拿到 intent 与 stealth 双达标的结果。 */
        PASSED("passed", "通过评审"),

        /** 未通过但产生了有效迭代：轮次耗尽或全部被拦截，保留 best-so-far 作为降级样本。 */
        DEGRADED("degraded", "未通过但有有效迭代"),

        /** 没有产生任何有效迭代：通常是反思轮次配置为 0，或第一轮开始前就退出。 */
        FAILED("failed", "未产生任何有效迭代"),

        /** 执行过程中抛出异常：模型调用失败、解析失败或持久化失败等。 */
        ERROR("error", "执行过程抛出异常");

        /** 指标标签值，直接写入 Prometheus 的 outcome 标签。 */
        private final String tag;

        /** 中文说明，用于日志和文档，不进入指标标签，避免标签值出现中文。 */
        private final String description;

        /**
         * @param tag         指标标签值，只能是英文小写，保持与既有看板兼容
         * @param description 中文说明，仅用于日志
         */
        Outcome(String tag, String description) {
            this.tag = tag;
            this.description = description;
        }

        /**
         * @return 指标标签值，例如 passed、degraded、failed、error
         */
        public String tag() {
            return tag;
        }

        /**
         * @return 中文说明，用于日志输出
         */
        public String description() {
            return description;
        }
    }
}
