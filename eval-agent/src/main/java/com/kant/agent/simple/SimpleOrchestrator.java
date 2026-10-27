package com.kant.agent.simple;

import com.kant.agent.simple.memory.IterationRecord;
import com.kant.agent.simple.memory.ReflectionMemory;
import com.kant.agent.simple.model.SampleTaskTicket;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 样本工厂Orchestrator，用于协调多个agent的执行。
 *
 * @author kant
 * @date 2024/08/10
 */
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

    public void runReflectionLoop(SampleTaskTicket.TaskTicket ticket, int maxIterations, double temperature) {
        runReflectionLoop(ticket, maxIterations, temperature, AgentExecutionListener.NOOP);
    }

    /**
     * @return 是否在迭代次数内获得通过评审的样本
     */
    public boolean runReflectionLoop(SampleTaskTicket.TaskTicket ticket, int maxIterations, double temperature,
                                     AgentExecutionListener listener) {
        log.info("=======================初始化Agent========================");
        SimpleJudgeAgent judgeAgent = new SimpleJudgeAgent();
        SimpleGenerateAgent generatorAgent = new SimpleGenerateAgent();
        log.info("=======================初始化Agent完成========================");

        // P1-CHANGE: 用 ReflectionMemory 替代 currentPrompt + currentAdvice
        ReflectionMemory memory = new ReflectionMemory();

        for (int i = 1; i <= maxIterations; i++) {
            listener.onIterationStarted(i);
            log.info("=== 开启第 {} 轮迭代（注入历史 {} 条） ===", i, memory.size());

            // 1. 生成（带完整历史记忆）
            String prompt = generatorAgent.generatePrompt(ticket, memory, temperature, listener);
            log.info("Actor 生成的 Prompt: \n{}", prompt);

            // 2. L1 意图归零检测
            if (isIntentLikelyEmpty(prompt, ticket.coreIntent())) {
                log.warn("❌ 意图归零：骨架完全不含核心意图关键词，跳过 Judge。");
                memory.record(IterationRecord.intentEmpty(i, prompt));
                continue;
            }

            // 3. Judge 评估
            SampleTaskTicket.JudgeFeedback feedback = judgeAgent.evaluate(ticket, prompt, listener);
            log.info("Judge 评分 -> 意图: {}, 隐蔽: {}, 及格: {}",
                    feedback.intentScore(), feedback.stealthScore(), feedback.isPass());

            if (feedback.isPass()) {
                // P1-CHANGE: 通过时也记录，便于日志审计（不再读取，但保留完整链路）
                memory.record(new IterationRecord(
                        i, prompt,
                        feedback.intentScore(), feedback.stealthScore(),
                        true, feedback.critique(), feedback.advice(),
                        IterationRecord.SOURCE_GENERATOR));
                log.info("🎉 样本及格！提前终止循环。");
                return true;
            }

            // 4. advice 安全对齐话术过滤
            String safeAdvice = isJailbrokenAdvice(feedback.advice()) ? "" : feedback.advice();
            if (safeAdvice.isEmpty() && feedback.advice() != null && !feedback.advice().isBlank()) {
                log.warn("⚠️ Judge 输出安全对齐话术，本轮 advice 作废。原文: {}", feedback.advice());
            }

            // 5. 记入记忆（用过滤后的 advice）
            memory.record(new IterationRecord(
                    i, prompt,
                    feedback.intentScore(), feedback.stealthScore(),
                    false, feedback.critique(), safeAdvice,
                    IterationRecord.SOURCE_GENERATOR));

            log.warn("❌ 样本不合格。缺陷: {} | 有效建议: {}",
                    feedback.critique(), safeAdvice.isEmpty() ? "（已过滤）" : safeAdvice);
        }

        // P1-CHANGE: 3 轮全失败，保留 best-so-far 供降级池
        memory.bestSoFar().ifPresentOrElse(
                best -> log.warn("达到最大迭代轮数 ({})。Best-so-far: 第 {} 轮 (intent={}, stealth={})",
                        maxIterations, best.iteration(), best.intentScore(), best.stealthScore()),
                () -> log.error("达到最大迭代轮数 ({})，无可用的 best-so-far，丢弃废料。", maxIterations)
        );
        return false;
    }

    // ─── P0 保留的检测方法 ───

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

    private static boolean isJailbrokenAdvice(String advice) {
        return advice != null && !advice.isBlank()
                && SAFETY_ALIGNMENT.matcher(advice).find();
    }
}