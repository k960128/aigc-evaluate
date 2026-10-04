package com.kant.llm.eval.agent.production.worker;

import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.service.budget.TokenEstimator;
import com.kant.agent.service.production.SampleGenerationWorkflow;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleRunDO;
import com.kant.llm.eval.agent.production.service.*;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 默认关闭的生产Worker。结构化生成与确定性质检迭代，不接入探测端口或Demo的自由工具循环。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "eval.agent.sample-factory.worker-enabled", havingValue = "true")
public class SampleProductionWorker {
    private final SampleRunRecordService records;
    private final SampleProductionService production;
    private final SampleGenerationPort generator;
    private final FactoryJsonSupport json;
    private final TaskExecutor executor;
    private final Map<String, AgentSampleRunDO> active = new ConcurrentHashMap<>();

    public SampleProductionWorker(SampleRunRecordService records, SampleProductionService production, SampleGenerationPort generator,
                                  FactoryJsonSupport json, @Qualifier("sampleProductionExecutor") TaskExecutor executor) {
        this.records = records;
        this.production = production;
        this.generator = generator;
        this.json = json;
        this.executor = executor;
    }

     @Scheduled(fixedDelayString="${eval.agent.sample-factory.recovery-interval-ms:5000}")
    public void dispatch() {
        for (var run : records.recoverable()) {
            String worker = "spworker_" + UUID.randomUUID().toString().replace("-", "");
            if (!records.claim(run.getRunId(), worker)) continue;
            active.put(worker, run);
            try {
                executor.execute(() -> execute(run, worker));
            } catch (RuntimeException ex) {
                active.remove(worker);
                release(run, worker);
                safeLog("生产批次未投递", run, ex);
            }
        }
    }

     @Scheduled(fixedDelay=5000L)
    public void heartbeat() {
        active.forEach((worker, run) -> {
            try {
                if (!records.heartbeat(run.getRunId(), worker)) active.remove(worker);
            } catch (RuntimeException ex) {
                safeLog("生产租约续租失败", run, ex);
            }
        });
    }

    private void execute(AgentSampleRunDO original, String worker) {
        try {
            advance(original, worker);
        } catch (RuntimeException ex) {
            safeLog("生产推进中断", original, ex);
            try {
                var r = records.authorized(original.getTenantId(), original.getActorId(), original.getRunId());
                if (r.getPendingOperationId() != null) records.block(r.getRunId(), worker);
                else if (Set.of("RUNNING", "BLOCKED").contains(r.getStatus()))
                    records.end(r.getRunId(), worker, "FAILED", "WORKFLOW_INTERRUPTED");
            } catch (RuntimeException ignored) { /* 失租/数据库不可用不覆盖原意图，新Worker按操作事实恢复。 */ }
        } finally {
            active.remove(worker);
            release(original, worker);
        }
    }

    private void advance(AgentSampleRunDO original, String worker) {
        while (true) {
            var run = records.authorized(original.getTenantId(), original.getActorId(), original.getRunId());
            if (!worker.equals(run.getWorkerId()) || !Set.of("RUNNING", "BLOCKED").contains(run.getStatus())) return;
            var task = production.authorizedTask(run.getTenantId(), run.getActorId(), run.getTaskId());
            var spec = production.spec(task);
            if (run.getPendingOperationId() == null) {
                if (Boolean.TRUE.equals(run.getStopRequested()) || !run.getDeadlineAt().isAfter(json.utc(Instant.now()))) {
                    boolean stopped = Boolean.TRUE.equals(run.getStopRequested());
                    records.end(run.getRunId(), worker, stopped ? "STOPPED" : "FAILED", stopped ? "STOP_REQUESTED" : "DEADLINE_EXPIRED");
                    return;
                }
                if ("BLOCKED".equals(run.getStatus())) return;
                var profile = generator.profile();
                if (!profile.available() || !profile.profileRef().equals(spec.generatorProfileRef())) {
                    records.end(run.getRunId(), worker, "FAILED", "GENERATOR_CONFIGURATION_CHANGED");
                    return;
                }
                var seed = production.seed(run.getTenantId(), run.getActorId(), spec.seedId());
                var parent = records.parent(run);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("seedId", seed.getSeedId());
                payload.put("seed", seed.getContent());
                payload.put("ordinal", run.getOrdinal());
                payload.put("rewriteRound", run.getRewriteRound());
                payload.put("riskId", spec.riskId());
                payload.put("mutations", spec.mutations());
                payload.put("topicTerms", spec.topicTerms());
                payload.put("minLength", spec.minLength());
                payload.put("maxLength", spec.maxLength());
                payload.put("parentContent", parent == null ? null : parent.getContent());
                payload.put("feedback", parent == null ? null : json.read(parent.getQualityJson(), com.kant.agent.service.production.SampleQualityWorkflow.Report.class));
                var command = new SampleGenerationPort.Command(records.operationId(run), profile.profileRef(), SampleGenerationWorkflow.SYSTEM_PROMPT, json.write(payload));
                long reservation = TokenEstimator.estimate(command.systemPrompt()) + TokenEstimator.estimate(command.userPayloadJson()) + profile.maxOutputTokens();
                if (!records.prepare(run.getRunId(), worker, command, reservation)) return;
                run = records.authorized(run.getTenantId(), run.getActorId(), run.getRunId());
            }
            var op = records.pending(run);
            if ("SUCCEEDED".equals(op.getStatus())) {
                records.settle(run.getRunId(), worker);
                continue;
            }
            if (!"ACCEPTED".equals(op.getStatus())) {
                records.block(run.getRunId(), worker);
                return;
            }
            if ("BLOCKED".equals(run.getStatus())) {
                records.cancelAccepted(run.getRunId(), worker, "PREPARATION_INTERRUPTED");
                return;
            }
            var command = json.read(op.getCommandJson(), SampleGenerationPort.Command.class);
            var profile = generator.profile();
            if (Boolean.TRUE.equals(run.getStopRequested()) || !run.getDeadlineAt().isAfter(json.utc(Instant.now()))
                    || !profile.available() || !profile.profileRef().equals(command.profileRef())) {
                records.cancelAccepted(run.getRunId(), worker, "STOP_DEADLINE_OR_CONFIGURATION");
                return;
            }
            if (!records.markCalling(run.getRunId(), worker)) {
                records.cancelAccepted(run.getRunId(), worker, "CALL_BOUNDARY_REJECTED");
                return;
            }
            try {
                records.recordResponse(op.getOperationId(), worker, generator.generate(command));
            } catch (RuntimeException ex) {
                records.recordUnknown(op.getOperationId(), worker);
                records.block(run.getRunId(), worker);
                safeLog("生成操作结果未确认", run, ex);
                return;
            }
        }
    }

    private void release(AgentSampleRunDO run, String worker) {
        try {
            records.release(run.getRunId(), worker);
        } catch (RuntimeException ex) {
            safeLog("生产释放租约失败", run, ex);
        }
    }

    private void safeLog(String reason, AgentSampleRunDO run, RuntimeException ex) {
        log.warn("D05{}，runId={}, errorType={}", reason, run.getRunId(), ex.getClass().getSimpleName());
    }
}
