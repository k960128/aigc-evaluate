package com.kant.springai.agent.service;

import com.kant.springai.agent.agent.multi.Agent;
import com.kant.springai.agent.agent.multi.ResearchAgent;
import com.kant.springai.agent.agent.multi.ReviewerAgent;
import com.kant.springai.agent.agent.multi.WriterAgent;
import com.kant.springai.agent.common.construct.AgentContext;
import com.kant.springai.agent.dto.ArticleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 编排器：负责 Agent 之间的调度与流程控制。
 *
 * 流程：
 *   调研 -> [写作 -> 审稿] * N（评分达标或达到最大轮次则退出）
 *
 * 这是一个「确定性流水线 + 迭代改进」的编排方式。
 * 若需要更动态的调度，可以把这里替换成 Supervisor 模式：
 * 用一个 LLM 读取 context 后决定下一步调用哪个 Agent。
 */
@Slf4j
@Service
public class ContentOrchestrator {
    /** 最大返工轮次，防止无限循环 */
    private static final int MAX_ROUNDS = 3;
    /** 通过分数线 */
    private static final int PASS_SCORE = 80;

    private final ResearchAgent researcher;
    private final WriterAgent writer;
    private final ReviewerAgent reviewer;

    public ContentOrchestrator(ResearchAgent researcher,
                               WriterAgent writer,
                               ReviewerAgent reviewer) {
        this.researcher = researcher;
        this.writer = writer;
        this.reviewer = reviewer;
    }

    public ArticleResult produce(String topic) {
        return produce(topic, _ -> { });
    }

    /**
     * @param progressListener 进度回调，可用于 SSE 推送
     */
    public ArticleResult produce(String topic, Consumer<String> progressListener) {
        long start = System.currentTimeMillis();
        AgentContext ctx = new AgentContext(topic);

        notify(progressListener, "任务开始：" + topic);

        // ---------- 阶段 1：调研 ----------
        runAgent(researcher, ctx, progressListener);

        // ---------- 阶段 2：写作 <-> 审稿 循环 ----------
        int round = 0;
        while (round < MAX_ROUNDS) {
            round++;
            ctx.round(round);
            notify(progressListener, "—— 第 %d 轮迭代 ——".formatted(round));

            // ---------- 阶段 2.1：写作 ----------
            runAgent(writer, ctx, progressListener);
            // ---------- 阶段 2.2：审稿 ----------
            runAgent(reviewer, ctx, progressListener);

            int score = ctx.getOrDefault(ReviewerAgent.KEY_SCORE, 0);
            if (score >= PASS_SCORE) {
                notify(progressListener, "得分 %d ≥ %d，通过评审 ✅".formatted(score, PASS_SCORE));
                break;
            }
            if (round == MAX_ROUNDS) {
                notify(progressListener, "得分 %d，已达最大轮次 %d，停止迭代 ⚠️"
                        .formatted(score, MAX_ROUNDS));
            } else {
                notify(progressListener, "得分 %d < %d，退回重写 🔁".formatted(score, PASS_SCORE));
            }
        }

        long cost = System.currentTimeMillis() - start;
        notify(progressListener, "任务完成，耗时 %d ms".formatted(cost));

        return new ArticleResult(
                topic,
                ctx.get(WriterAgent.KEY_DRAFT),
                ctx.getOrDefault(ReviewerAgent.KEY_SCORE, 0),
                ctx.getOrDefault(ReviewerAgent.KEY_ISSUES, List.of()),
                ctx.get(ReviewerAgent.KEY_SUMMARY),
                round,
                ctx.trace(),
                cost
        );
    }

    private void runAgent(Agent agent, AgentContext ctx, Consumer<String> listener) {
        notify(listener, "▶ %s 开始执行…".formatted(agent.name()));
        try {
            agent.execute(ctx);
        } catch (Exception e) {
            log.error("Agent [{}] 执行失败", agent.name(), e);
            ctx.log("[%s] 执行失败：%s".formatted(agent.name(), e.getMessage()));
            notify(listener, "❌ %s 执行失败：%s".formatted(agent.name(), e.getMessage()));
            throw new IllegalStateException("Agent 执行失败: " + agent.name(), e);
        }
        notify(listener, "✅ %s 执行完成".formatted(agent.name()));
    }

    private void notify(Consumer<String> listener, String message) {
        log.info("[Orchestrator] {}", message);
        try {
            listener.accept(message);
        } catch (Exception ignored) {
            // SSE 连接断开等场景，忽略回调异常，不影响主流程
        }
    }
}