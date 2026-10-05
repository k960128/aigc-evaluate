package com.kant.llm.eval.agent.production.service;

import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.port.production.SampleReflectionPort;
import com.kant.agent.service.budget.TokenEstimator;
import com.kant.agent.service.production.SampleQualityWorkflow;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleRunDO;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Reflection Agent的持久化执行适配：逐步执行Agent提示词与决策，而非一次call隐藏整个反思循环。
 * <p>本类无大事务。每次生成/评审均经过prepare、markCalling、确认响应和settle四个边界，
 * 网络请求在事务外，模型不能跳过额度、停止、租约或人工准入。</p>
 * <p>while仅推进数据库检查点；生成、评审、改写的分支及轮次由SampleReflectionAgent统一决定。</p>
 */
@Slf4j
@Service
public class SampleReflectionRunner {
    /** 独立短事务保存操作与检查点。 */
    private final SampleRunRecordService records;
    /** 读取冻结计划与授权种子。 */
    private final SampleProductionService production;
    /** Actor单次生成端口。 */
    private final SampleGenerationPort generator;
    /** Critic单次评审端口，不在内部改写。 */
    private final SampleReflectionPort critic;
    /** 唯一的提示词和有限改写决策核心。 */
    private final SampleReflectionAgent agent;
    /** 序列化与UTC时间工具。 */
    private final FactoryJsonSupport json;

    /** Actor和Critic为不同端口，当前Ollama适配器共用一个配置快照，不共享对话记忆。 */
    public SampleReflectionRunner(SampleRunRecordService records, SampleProductionService production,
                                  SampleGenerationPort generator, SampleReflectionPort critic,
                                  SampleReflectionAgent agent, FactoryJsonSupport json) {
        this.records = records;
        this.production = production;
        this.generator = generator;
        this.critic = critic;
        this.agent = agent;
        this.json = json;
    }

    /**
     * 从持久化检查点恢复直至批次结束或阻塞；不重发CALLING/UNKNOWN，不等待人工审核。
     * @param original 领取时的批次身份，只用于定位，不当作最新业务状态
     * @param worker 本次领取的租约主体；事务写入时再次核验租约
     */
    public void advance(AgentSampleRunDO original, String worker) {
        while (true) {
            var run = records.authorized(original.getTenantId(), original.getActorId(), original.getRunId());
            if (!worker.equals(run.getWorkerId()) || !Set.of("RUNNING", "BLOCKED").contains(run.getStatus())) return;
            if (run.getPendingOperationId() == null) {
                if (!SampleReflectionAgent.VERSION.equals(run.getWorkflowVersion())) {
                    records.end(run.getRunId(), worker, "FAILED", "REFLECTION_UPGRADE_REQUIRED");
                    return;
                }
                if (Boolean.TRUE.equals(run.getStopRequested()) || expired(run)) {
                    boolean stopped = Boolean.TRUE.equals(run.getStopRequested());
                    records.end(run.getRunId(), worker, stopped ? "STOPPED" : "FAILED", stopped ? "STOP_REQUESTED" : "DEADLINE_EXPIRED");
                    return;
                }
                if ("BLOCKED".equals(run.getStatus())) return;
                var task = production.authorizedTask(run.getTenantId(), run.getActorId(), run.getTaskId());
                var spec = production.spec(task);
                var profile = generator.profile();
                if (!profile.available() || !profile.profileRef().equals(spec.generatorProfileRef())) {
                    records.end(run.getRunId(), worker, "FAILED", "GENERATOR_CONFIGURATION_CHANGED");
                    return;
                }
                var seed = production.seed(run.getTenantId(), run.getActorId(), spec.seedId());
                var phase = records.phase(run);
                var parent = records.parent(run);
                var candidate = phase == SampleReflectionAgent.Phase.REFLECT ? records.currentCandidate(run) : null;
                var context = new SampleReflectionAgent.Context(spec, seed.getContent(), run.getOrdinal(), run.getRewriteRound(),
                        parent == null ? null : parent.getContent(),
                        parent == null ? null : json.read(parent.getQualityJson(), SampleQualityWorkflow.Report.class),
                        candidate == null ? null : candidate.getContent());
                var prompt = agent.prompt(phase, context);
                var command = new SampleGenerationPort.Command(records.operationId(run), profile.profileRef(),
                        prompt.systemPrompt(), json.write(prompt.payload()));
                long reservation = TokenEstimator.estimate(command.systemPrompt())
                        + TokenEstimator.estimate(command.userPayloadJson()) + profile.maxOutputTokens();
                // Critic也必须单独占用一次调用和Token预留，绝不能只在Actor处记账。
                if (!records.prepare(run.getRunId(), worker, command, reservation)) return;
                run = records.authorized(run.getTenantId(), run.getActorId(), run.getRunId());
            }

            var op = records.pending(run);
            if ("SUCCEEDED".equals(op.getStatus())) {
                // 响应已落库时只结算；配置变化、原发送者失租或服务重启都不能导致重复付费调用。
                records.settle(run.getRunId(), worker);
                continue;
            }
            if (!"ACCEPTED".equals(op.getStatus())) {
                records.block(run.getRunId(), worker);
                return;
            }
            if (!SampleReflectionAgent.VERSION.equals(run.getWorkflowVersion())) {
                records.cancelAccepted(run.getRunId(), worker, "REFLECTION_UPGRADE_REQUIRED");
                return;
            }
            if ("BLOCKED".equals(run.getStatus())) {
                records.cancelAccepted(run.getRunId(), worker, "PREPARATION_INTERRUPTED");
                return;
            }
            var command = json.read(op.getCommandJson(), SampleGenerationPort.Command.class);
            var profile = generator.profile();
            if (Boolean.TRUE.equals(run.getStopRequested()) || expired(run)
                    || !profile.available() || !profile.profileRef().equals(command.profileRef())) {
                records.cancelAccepted(run.getRunId(), worker, "STOP_DEADLINE_OR_CONFIGURATION");
                return;
            }
            if (!records.markCalling(run.getRunId(), worker)) {
                records.cancelAccepted(run.getRunId(), worker, "CALL_BOUNDARY_REJECTED");
                return;
            }
            try {
                if (records.phase(run) == SampleReflectionAgent.Phase.REFLECT)
                    records.recordReflectionResponse(op.getOperationId(), worker, critic.reflect(command));
                else
                    records.recordResponse(op.getOperationId(), worker, generator.generate(command));
            } catch (RuntimeException ex) {
                records.recordUnknown(op.getOperationId(), worker);
                records.block(run.getRunId(), worker);
                // 不输出种子、候选、反馈或供应商异常消息；角色字段区分哪笔调用未确认。
                log.warn("D05模型操作未确认，runId={}, operationId={}, kind={}, errorType={}",
                        run.getRunId(), op.getOperationId(), op.getOperationKind(), ex.getClass().getSimpleName());
                return;
            }
        }
    }

    /** 原固定期限包含排队和评审，进入下一阶段不延长截止时间。 */
    private boolean expired(AgentSampleRunDO run) {
        return !run.getDeadlineAt().isAfter(json.utc(Instant.now()));
    }
}
