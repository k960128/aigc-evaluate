package com.kant.llm.eval.agent.redteam.config;

import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.port.redteam.AdaptiveAttackAgent;
import com.kant.agent.port.redteam.RedTeamRunRecorder;
import com.kant.agent.port.redteam.RedTeamRunStarter;
import com.kant.agent.port.redteam.RedTeamTaskRepository;
import com.kant.agent.service.redteam.AttackActionValidator;
import com.kant.agent.service.redteam.EvaluationOrchestrator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * D02 编排器的 Spring 装配配置。
 *
 * <p>编排器本身保持纯 Java，不暴露 Spring AI 类型；具体端口由 eval 模块在
 * 这里注入。这样后续替换 Agent 实现时，不需要改动任务状态和目标执行流程。</p>
 */
@Configuration
public class RedTeamAgentConfiguration {

    /** 创建确定性的动作校验器。 */
    @Bean
    public AttackActionValidator attackActionValidator() {
        return new AttackActionValidator();
    }

    /** 创建 D02 自动红队编排器。 */
    @Bean
    public EvaluationOrchestrator evaluationOrchestrator(
            RedTeamTaskRepository taskRepository,
            RedTeamRunRecorder runRecorder,
            RedTeamRunStarter runStarter,
            AdaptiveAttackAgent attackAgent,
            AttackActionValidator actionValidator,
            ProbeExecutionPort probeExecutionPort,
            EvidenceReadPort evidenceReadPort) {
        return new EvaluationOrchestrator(
                taskRepository,
                runRecorder,
                runStarter,
                attackAgent,
                actionValidator,
                probeExecutionPort,
                evidenceReadPort);
    }
}
