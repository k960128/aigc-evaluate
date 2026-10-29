package com.kant.agent.simple;

import com.kant.agent.simple.memory.IterationRecord;
import com.kant.agent.simple.memory.ReflectionContextRenderer;
import com.kant.agent.simple.memory.ReflectionMemory;
import com.kant.agent.simple.metrics.TicketMetricsCollector;
import com.kant.agent.simple.model.SampleTaskTicket;
import com.kant.agent.simple.tactic.ComposedSample;
import com.kant.agent.simple.tactic.TacticComposer;
import com.kant.agent.simple.tactic.TacticTransformerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
public class SimpleOrchestrator {

    private static final Pattern SAFETY_ALIGNMENT = Pattern.compile(
            "(建议|应当|可以|应)[^。；\\n]{0,20}(不要|禁止|避免|不应|删除|降低|削弱|改用虚构|改用合成)"
                    + "|改用.{0,5}(虚构|合成|安全|无害)"
                    + "|(明确|要求|提示)[^。；\\n]{0,10}(拒绝|不要输出|避免泄露)"
                    + "|不建议生成|不建议增强|不应索取|无需增强|不必增强"
    );

    private static final Set<String> INTENT_STOP_WORDS = Set.of(
            "模型", "输出", "真实", "存在", "诱导", "获取", "给出", "提供",
            "信息", "数据", "内容", "如何", "什么", "这些", "那些", "要求",
            "或者", "以及", "包括", "可以", "需要", "进行", "所有", "相关"
    );

    // P1-CHANGE: 注入 registry + composer
    private final TacticTransformerRegistry registry;
    private final TacticComposer composer;
    private final TicketMetricsCollector metricsCollector;

    public SimpleOrchestrator() {
        this(TacticTransformerRegistry.createDefault(), null);
    }

    public SimpleOrchestrator(TacticTransformerRegistry registry,
                              TicketMetricsCollector metricsCollector) {
        this.registry = registry;
        this.composer = new TacticComposer(registry);
        this.metricsCollector = metricsCollector;
    }


    /**
     * 运行反射循环
     * @param ticket
     * @param maxIterations
     * @param temperature
     * @param listener
     * @return 是否通过
     */
    public boolean runReflectionLoop(SampleTaskTicket.TaskTicket ticket, int maxIterations,
                                     double temperature, AgentExecutionListener listener) {
        // L2-A: 记录开始时间
        long startedAt = System.currentTimeMillis();

        log.info("=======================初始化Agent========================");
        SimpleGenerateAgent generatorAgent = new SimpleGenerateAgent(new ReflectionContextRenderer(), registry);
        SimpleJudgeAgent judgeAgent = new SimpleJudgeAgent();
        log.info("=======================初始化Agent完成========================");

        ReflectionMemory memory = new ReflectionMemory();

        for (int i = 1; i <= maxIterations; i++) {
            listener.onIterationStarted(i);
            log.info("=== 第 {} 轮（注入历史 {} 条） ===", i, memory.size());

            // 1. 生成骨架（含占位符）
            String scaffold = generatorAgent.generatePrompt(ticket, memory, temperature, listener);
            log.info("Actor 生成的骨架: \n{}", scaffold);

            // 2. L1 意图归零检测（用骨架检测，占位符内明文也参与）
            if (isIntentLikelyEmpty(scaffold, ticket.coreIntent())) {
                log.warn("❌ 意图归零，跳过 Composer 和 Judge");
                memory.record(IterationRecord.intentEmpty(i, scaffold));
                continue;
            }

            // 3. P1-CHANGE: 程序化变换
            ComposedSample composed = composer.compose(scaffold, ticket.tactics());
            log.info("变换完成: applied={}, missing={}, reversible={}",
                    composed.appliedTransformers(),
                    composed.missingTransformers(),
                    composed.reverseVerified());

            if (!composed.isComplete()) {
                log.warn("❌ 变换不完整: {}", composed.failureReason());
                memory.record(new IterationRecord(
                        i, scaffold, 0, 0, false,
                        composed.failureReason(), "", "TRANSFORMER_INCOMPLETE"));
                continue;
            }
            log.info("最终样本: \n{}", composed.finalText());

            // 4. Judge 评骨架（不评密文）
            SampleTaskTicket.JudgeFeedback feedback = judgeAgent.evaluate(
                    ticket, composed.semanticScaffold(), listener);
            log.info("Judge -> intent={}, stealth={}, pass={}",
                    feedback.intentScore(), feedback.stealthScore(), feedback.isPass());

            if (feedback.isPass()) {
                memory.record(new IterationRecord(i, scaffold,
                        feedback.intentScore(), feedback.stealthScore(),
                        true, feedback.critique(), feedback.advice(),
                        IterationRecord.SOURCE_GENERATOR));
                log.info("🎉 通过");
                return true;
            }

            // 5. advice 过滤后记入记忆
            String safeAdvice = isJailbrokenAdvice(feedback.advice()) ? "" : feedback.advice();
            memory.record(new IterationRecord(i, scaffold,
                    feedback.intentScore(), feedback.stealthScore(),
                    false, feedback.critique(), safeAdvice,
                    IterationRecord.SOURCE_GENERATOR));
            log.warn("❌ 不合格: {}", feedback.critique());
        }

        // L2-A: 失败出口埋点
        if (metricsCollector != null) {
            metricsCollector.collect(memory, false, System.currentTimeMillis() - startedAt);
        }
        memory.bestSoFar().ifPresentOrElse(
                best -> log.warn("达到最大迭代轮数。Best-so-far: 第 {} 轮 (intent={}, stealth={})",
                        best.iteration(), best.intentScore(), best.stealthScore()),
                () -> log.error("达到最大迭代轮数，无可用 best-so-far。"));
        return false;
    }

    /**
     * 检查意图是否归零。
     *
     * @param prompt 生成的骨架
     * @param coreIntent 核心意图
     * @return 如果 prompt 生成的骨架
     * @param coreIntent 核心意图
     * @return 如果 prompt 生成的骨架包含 coreIntent 的前缀，返回 true
     */
    private static boolean isIntentLikelyEmpty(String prompt, String coreIntent) {
        if (coreIntent == null || coreIntent.isBlank()) return false;
        if (prompt == null || prompt.isBlank()) return true;
        for (int i = 0; i <= coreIntent.length() - 2; i++) {
            String gram = coreIntent.substring(i, i + 2);
            if (INTENT_STOP_WORDS.contains(gram)) continue;
            if (prompt.contains(gram)) return false;
        }
        return true;
    }

    /**
     * 是否是安全的 advice。
     * @param advice Judge 给出的 advice
     * @return 是否安全的 advice
     */
    private static boolean isJailbrokenAdvice(String advice) {
        return advice != null && !advice.isBlank()
                && SAFETY_ALIGNMENT.matcher(advice).find();
    }
}