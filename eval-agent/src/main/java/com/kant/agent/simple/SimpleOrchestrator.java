package com.kant.agent.simple;

import com.kant.agent.simple.model.SampleTaskTicket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 样本工厂Orchestrator，用于协调多个agent的执行。
 *
 * @author kant
 * @date 2024/08/10
 */
@Slf4j
public class SimpleOrchestrator {

    public String runReflectionLoop(SampleTaskTicket.TaskTicket ticket, int maxIterations, double temperature) {
        log.info("=======================初始化Agent========================");
        SimpleJudgeAgent judgeAgent = new SimpleJudgeAgent();
        SimpleGenerateAgent generatorAgent = new SimpleGenerateAgent();
        log.info("=======================初始化Agent完成========================");

        String currentPrompt = null;
        String currentAdvice = null;

        for (int i = 1; i <= maxIterations; i++) {
            log.info("=== 开启第 {} 轮迭代 ===", i);

            // 1. Actor 生成 (如果 currentAdvice 有值，则进行反思重写)
            currentPrompt = generatorAgent.generatePrompt(ticket, currentPrompt, currentAdvice, temperature);
            log.info("Actor 生成的 Prompt: \n{}", currentPrompt);

            // 2. Judge 评估
            SampleTaskTicket.JudgeFeedback feedback = judgeAgent.evaluate(ticket, currentPrompt);
            log.info("Judge 评分 -> 意图: {}, 隐蔽: {}, 及格: {}",
                    feedback.intentScore(), feedback.stealthScore(), feedback.isPass());

            // 3. 结果判定
            if (feedback.isPass()) {
                log.info("🎉 样本及格！提前终止循环。");
                return currentPrompt; // 成功，入库
            } else {
                log.warn("❌ 样本不合格。缺陷: {} | 建议: {}", feedback.critique(), feedback.advice());
                // 将 Judge 的建议传给下一轮的 Actor
                currentAdvice = feedback.advice();
            }
        }

        log.error("达到最大迭代轮数 ({})，生成失败，丢弃废料。", maxIterations);
        return null; // 失败，丢弃
    }
}
