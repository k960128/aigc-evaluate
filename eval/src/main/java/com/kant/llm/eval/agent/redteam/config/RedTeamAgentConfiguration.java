package com.kant.llm.eval.agent.redteam.config;

import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.port.redteam.AdaptiveAttackAgent;
import com.kant.agent.port.redteam.RedTeamRunRecorder;
import com.kant.agent.port.redteam.RedTeamRunStarter;
import com.kant.agent.port.redteam.RedTeamTaskRepository;
import com.kant.agent.service.redteam.AttackActionValidator;
import com.kant.agent.service.redteam.EvaluationOrchestrator;
import com.kant.agent.service.redteam.MultiTurnEvaluationOrchestrator;
import com.kant.agent.port.target.TargetCapabilityPort;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

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
            @Qualifier("ruleBasedAdaptiveAttackAgent") AdaptiveAttackAgent attackAgent,
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

    /**
     * D03 后台 Worker 使用的有限线程池。
     *
     * <p>线程池只负责异步推进，数据库运行和会话检查点才是恢复依据。</p>
     */
    @Bean
    public TaskExecutor redTeamWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        // 不排队已抢占的租约，避免运行尚未获得线程租约就过期。
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("red-team-worker-");
        executor.initialize();
        return executor;
    }

    /** 创建 D03 线性多轮后台编排器。 */
    @Bean
    public MultiTurnEvaluationOrchestrator multiTurnEvaluationOrchestrator(
            RedTeamTaskRepository taskRepository,
            RedTeamRunRecorder runRecorder,
            RedTeamRunStarter runStarter,
            @Qualifier("ruleBasedMultiTurnAttackAgent") AdaptiveAttackAgent attackAgent,
            AttackActionValidator actionValidator,
            ProbeExecutionPort probeExecutionPort,
            EvidenceReadPort evidenceReadPort,
            TargetCapabilityPort targetCapabilityPort) {
        return new MultiTurnEvaluationOrchestrator(
                taskRepository,
                runRecorder,
                runStarter,
                attackAgent,
                actionValidator,
                probeExecutionPort,
                evidenceReadPort,
                targetCapabilityPort);
    }
}
