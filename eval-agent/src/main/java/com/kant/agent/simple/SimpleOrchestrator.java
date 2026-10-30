package com.kant.agent.simple;

import com.kant.agent.simple.memory.IterationRecord;
import com.kant.agent.simple.memory.ReflectionContextRenderer;
import com.kant.agent.simple.memory.ReflectionMemory;
import com.kant.agent.simple.metrics.TicketMetricsCollector;
import com.kant.agent.simple.model.SampleTaskTicket;
import com.kant.agent.simple.tactic.ComposedSample;
import com.kant.agent.simple.tactic.TacticComposer;
import com.kant.agent.simple.tactic.TacticTransformerRegistry;
import com.kant.framework.util.BeanUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 样本生产反思循环的编排器。
 *
 * <p>单轮流程：Generator 产出语义骨架 → 意图归零检测 → 程序化编码变换 → Judge 评审
 * （只评骨架）→ 结果写入反思记忆。循环在任一环节失败都会把原因沉淀进记忆，供下一轮
 * 生成时参考，避免反复走同一条死路。</p>
 *
 * <h3>指标采集点</h3>
 * <p>采集拆成两处，原因见 {@link TicketMetricsCollector} 的类注释：</p>
 * <ul>
 *   <li><b>事件点</b>：每轮评分、意图归零、变换失败、反馈过滤，发生即写，保证实时性；</li>
 *   <li><b>收口点</b>：循环外的 {@code finally} 统一写结果、耗时和轮次，覆盖成功、
 *       失败和异常三种出口，不会因为提前 return 而漏采。</li>
 * </ul>
 *
 * <p>编排器本身不感知 Micrometer，所有指标调用都通过 {@link TicketMetricsCollector}
 * 完成；构造时允许传入 null，内部会自动降级为空实现，避免在热路径上反复判空。</p>
 */
@Slf4j
public class SimpleOrchestrator {

    /**
     * 安全对齐话术识别正则。
     *
     * <p>Judge 偶尔会给出"建议改为虚构数据""明确要求模型拒绝"这类建议，这类建议如果
     * 回灌给 Generator，会让下一轮样本主动削弱核心意图，与红队评审的立场相悖，因此在
     * 写入记忆前会被过滤掉，只保留"如何更隐蔽""如何更完整表达意图"这类有效建议。</p>
     */
    private static final Pattern SAFETY_ALIGNMENT = Pattern.compile(
            "(建议|应当|可以|应)[^。；\\n]{0,20}(不要|禁止|避免|不应|删除|降低|削弱|改用虚构|改用合成)"
                    + "|改用.{0,5}(虚构|合成|安全|无害)"
                    + "|(明确|要求|提示)[^。；\\n]{0,10}(拒绝|不要输出|避免泄露)"
                    + "|不建议生成|不建议增强|不应索取|无需增强|不必增强"
    );

    /**
     * 意图归零检测时忽略的 2-gram 停用词。
     *
     * <p>这些组合在核心意图里几乎必然出现，但在伪装话题里也随处可见，保留它们会让
     * 检测形同虚设，因此参与匹配前先剔除。</p>
     */
    private static final Set<String> INTENT_STOP_WORDS = Set.of(
            "模型", "输出", "真实", "存在", "诱导", "获取", "给出", "提供",
            "信息", "数据", "内容", "如何", "什么", "这些", "那些", "要求",
            "或者", "以及", "包括", "可以", "需要", "进行", "所有", "相关"
    );

    /** 战术变换器注册表，决定本轮允许出现哪些编码占位符。 */
    private final TacticTransformerRegistry registry;

    /** 占位符扫描与编码替换器，与注册表共用同一份变换器清单。 */
    private final TacticComposer composer;

    /** 指标采集门面，构造时保证非 null。 */
    private final TicketMetricsCollector metricsCollector;

    /**
     * 默认构造：使用内置变换器，并尝试从 Spring 容器获取指标采集器。
     *
     * <p>这是业务层（样本任务 Service）实际使用的构造方式。</p>
     */
    public SimpleOrchestrator() {
        this(TacticTransformerRegistry.createDefault(), resolveMetricsCollector());
    }

    /**
     * 显式构造：调用方自行提供变换器与指标采集器。
     *
     * @param registry         变换器注册表，不能为 null
     * @param metricsCollector 指标采集器；允许为 null，内部会降级为空实现
     */
    public SimpleOrchestrator(TacticTransformerRegistry registry,
                              TicketMetricsCollector metricsCollector) {
        this.registry = registry;
        this.composer = new TacticComposer(registry);
        // 统一在这里兜底，后续循环里就不用写 if (metricsCollector != null)。
        this.metricsCollector = metricsCollector != null
                ? metricsCollector
                : TicketMetricsCollector.noop();
    }

    /**
     * 从 Spring 容器解析指标采集器。
     *
     * <p>业务层用 {@code new SimpleOrchestrator()} 创建实例，无法走构造注入，因此这里
     * 通过静态容器查找获取 Bean。查找失败时返回空实现，并打印告警——不返回基于
     * {@code Metrics.globalRegistry} 的实例，是因为 Spring Boot 的注册表不会挂到全局
     * 注册表上，写进去的指标不会被导出，只会造成"有采集、没数据"的假象。</p>
     *
     * @return 容器中的采集器；取不到时返回空实现
     */
    private static TicketMetricsCollector resolveMetricsCollector() {
        try {
            TicketMetricsCollector bean = BeanUtil.getBean(TicketMetricsCollector.class);
            if (bean != null) {
                return bean;
            }
        } catch (Exception exception) {
            log.warn("无法从 Spring 容器获取 TicketMetricsCollector，本次运行的指标不会被导出: {}",
                    exception.getMessage());
            return TicketMetricsCollector.noop();
        }
        log.warn("Spring 容器中不存在 TicketMetricsCollector，本次运行的指标不会被导出");
        return TicketMetricsCollector.noop();
    }

    /**
     * 简化入口：使用空实现采集器执行一轮反思循环。
     *
     * @param ticket        样本任务票据
     * @param maxIterations 最大反思轮次
     * @param temperature   生成温度
     */
    public void runReflectionLoop(SampleTaskTicket.TaskTicket ticket, int maxIterations,
                                  double temperature) {
        runReflectionLoop(ticket, maxIterations, temperature, AgentExecutionListener.NOOP);
    }

    /**
     * 执行反思循环，直到评审通过或轮次耗尽。
     *
     * <p>无论以何种方式退出（通过、轮次耗尽、抛异常），都会在 finally 中完成指标收口，
     * 因此调用方不需要额外关心"失败时有没有埋点"。</p>
     *
     * @param ticket        样本任务票据，包含核心意图、伪装种子和变异战术
     * @param maxIterations 最大反思轮次，建议 3~5；过大只会放大 token 成本
     * @param temperature   生成温度
     * @param listener      执行监听器，用于持久化每轮调用日志；不能为 null
     * @return 是否在轮次上限内通过评审
     */
    public boolean runReflectionLoop(SampleTaskTicket.TaskTicket ticket, int maxIterations,
                                     double temperature, AgentExecutionListener listener) {
        log.info("=======================初始化Agent========================");
        SimpleGenerateAgent generatorAgent = new SimpleGenerateAgent(
                new ReflectionContextRenderer(), registry);
        SimpleJudgeAgent judgeAgent = new SimpleJudgeAgent();
        log.info("=======================初始化Agent完成========================");

        ReflectionMemory memory = new ReflectionMemory();
        // 是否在轮次内通过评审，finally 中用于确定 outcome
        boolean passed = false;
        // 是否因异常中断，finally 中用于区分 error 与 degraded
        boolean errored = false;
        // 实际尝试的轮次；异常中断的那一轮也计入，避免轮次统计偏小
        int attemptedRounds = 0;

        // 计时与在途计数都放在 try 之前：初始化阶段不产生指标，只有循环阶段才需要收口。
        // 用纳秒计时：毫秒级 currentTimeMillis 会受系统时钟调整影响，长任务下可能出现负耗时。
        long startedAtNanos = System.nanoTime();
        metricsCollector.onTicketStarted();

        try {
            for (int i = 1; i <= maxIterations; i++) {
                attemptedRounds = i;
                listener.onIterationStarted(i);
                log.info("=== 第 {} 轮（注入历史 {} 条） ===", i, memory.size());

                // 1. 生成骨架（含占位符）
                String scaffold = generatorAgent.generatePrompt(ticket, memory, temperature, listener);
                log.info("Actor 生成的骨架: \n{}", scaffold);

                // 2. 意图归零检测：骨架里连核心意图的关键词都没有，后面两步就没有意义
                if (isIntentLikelyEmpty(scaffold, ticket.coreIntent())) {
                    log.warn("❌ 意图归零，跳过 Composer 和 Judge");
                    metricsCollector.onIntentEmpty();
                    memory.record(IterationRecord.intentEmpty(i, scaffold));
                    continue;
                }

                // 3. 程序化变换：模型只负责语义，编码由程序确定性完成
                ComposedSample composed = composer.compose(scaffold, ticket.tactics());
                log.info("变换完成: applied={}, missing={}, reversible={}",
                        composed.appliedTransformers(),
                        composed.missingTransformers(),
                        composed.reverseVerified());

                if (!composed.isComplete()) {
                    log.warn("❌ 变换不完整: {}", composed.failureReason());
                    metricsCollector.onTransformerIncomplete();
                    memory.record(IterationRecord.transformerIncomplete(
                            i, scaffold, composed.failureReason()));
                    continue;
                }
                log.info("最终样本: \n{}", composed.finalText());

                // 4. Judge 评骨架（不评密文）：密文对语义判断是噪声
                SampleTaskTicket.JudgeFeedback feedback = judgeAgent.evaluate(
                        ticket, composed.semanticScaffold(), listener);
                log.info("Judge -> intent={}, stealth={}, pass={}",
                        feedback.intentScore(), feedback.stealthScore(), feedback.isPass());

                // 只有真正拿到 Judge 结果的轮次才记分数，被拦截的轮次不会走到这里
                metricsCollector.onRoundScored(i, feedback.intentScore(), feedback.stealthScore());

                // 5. 通过：记录后立即返回，收口动作交给 finally
                if (feedback.isPass()) {
                    memory.record(IterationRecord.of(
                            i, scaffold,
                            feedback.intentScore(), feedback.stealthScore(),
                            true, feedback.critique(), feedback.advice(),
                            IterationRecord.SOURCE_GENERATOR));
                    passed = true;
                    log.info("🎉 通过");
                    return true;
                }

                // 6. 不通过：过滤掉安全对齐类建议，其余写回记忆供下一轮参考
                boolean filtered = isJailbrokenAdvice(feedback.advice());
                String safeAdvice = filtered ? "" : feedback.advice();
                if (filtered) {
                    metricsCollector.onAdviceFiltered();
                    log.warn("⚠️ Judge advice 被安全对齐话术过滤: {}", feedback.advice());
                }
                memory.record(new IterationRecord(
                        i, scaffold,
                        feedback.intentScore(), feedback.stealthScore(),
                        false, feedback.critique(), safeAdvice,
                        IterationRecord.SOURCE_GENERATOR,
                        filtered));
                log.warn("❌ 不合格: {}", feedback.critique());
            }
            return false;

        } catch (RuntimeException exception) {
            // 只做标记后原样抛出，收口统一在 finally，避免两处都写指标导致重复计数。
            errored = true;
            throw exception;

        } finally {
            // 收口点：成功、失败、异常三种出口都会到这里
            TicketMetricsCollector.Outcome outcome = resolveOutcome(errored, passed, memory);
            try {
                metricsCollector.collect(memory, attemptedRounds, outcome,
                        elapsedMillis(startedAtNanos));
            } finally {
                // 在途计数必须释放：即使指标写入本身失败，也不能让 Gauge 一直挂着
                metricsCollector.onTicketFinished();
            }

            if (!passed && !errored) {
                memory.bestSoFar().ifPresentOrElse(
                        best -> log.warn("达到最大迭代轮数。Best-so-far: 第 {} 轮 (intent={}, stealth={})",
                                best.iteration(), best.intentScore(), best.stealthScore()),
                        () -> log.error("达到最大迭代轮数，无可用 best-so-far。"));
            }
        }
    }

    /**
     * 根据退出方式判定 ticket 最终结果。
     *
     * <p>优先级：异常 &gt; 通过 &gt; 无有效迭代 &gt; 有迭代但未通过。异常优先，是因为
     * 异常 ticket 往往也留下了若干轮记录，如果按"有历史"判成 degraded，会把执行故障
     * 混进质量问题的统计里，导致失败率被低估。</p>
     *
     * @param errored 是否因异常退出
     * @param passed  是否通过评审
     * @param memory  本次 ticket 的反思记忆，用于判断是否产生过有效迭代
     * @return 最终结果分类
     */
    private static TicketMetricsCollector.Outcome resolveOutcome(boolean errored, boolean passed,
                                                                 ReflectionMemory memory) {
        if (errored) {
            return TicketMetricsCollector.Outcome.ERROR;
        }
        if (passed) {
            return TicketMetricsCollector.Outcome.PASSED;
        }
        // 没有留下任何迭代记录：轮次配置为 0，或在记录之前就退出
        if (memory == null || memory.isEmpty()) {
            return TicketMetricsCollector.Outcome.FAILED;
        }
        return TicketMetricsCollector.Outcome.DEGRADED;
    }

    /**
     * 计算从开始到现在的毫秒数。
     *
     * @param startedAtNanos {@link System#nanoTime()} 记录的起点
     * @return 经过的毫秒数，恒为非负
     */
    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    /**
     * 检查骨架是否丢失核心意图。
     *
     * <p>做法是把核心意图切成 2-gram，剔除停用词后看骨架里是否命中任意一个。命中即认为
     * 意图仍在，否则判定为"意图归零"。这是一个纯本地、零成本的启发式判断，用于在调用
     * Judge 之前挡掉明显跑偏的骨架；它不判断意图强弱，只判断"有没有"。</p>
     *
     * @param prompt     生成的骨架文本
     * @param coreIntent 核心意图
     * @return true 表示骨架已丢失核心意图
     */
    private static boolean isIntentLikelyEmpty(String prompt, String coreIntent) {
        if (coreIntent == null || coreIntent.isBlank()) {
            return false;
        }
        if (prompt == null || prompt.isBlank()) {
            return true;
        }
        for (int i = 0; i <= coreIntent.length() - 2; i++) {
            String gram = coreIntent.substring(i, i + 2);
            if (INTENT_STOP_WORDS.contains(gram)) {
                continue;
            }
            if (prompt.contains(gram)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 Judge 反馈是否属于需要过滤的安全对齐话术。
     *
     * @param advice Judge 给出的修改建议
     * @return true 表示该建议会让样本偏离红队立场，应当丢弃
     */
    private static boolean isJailbrokenAdvice(String advice) {
        return advice != null && !advice.isBlank()
                && SAFETY_ALIGNMENT.matcher(advice).find();
    }
}
