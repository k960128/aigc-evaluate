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

/**
 * 后台样本生产执行器，需显式开启worker-enabled；按数据库检查点串行推进一个批次。
 * <p>dispatch领取租约并提交有限线程池，heartbeat续租，advance负责生成/结算循环。
 * 循环受槽位、改写次数、预算、截止时间和停止意图约束，不是模型自由决策的工具循环。</p>
 * <p>不接入被测目标；人工审核独立于生成执行器，不占线程等待。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "eval.agent.sample-factory.worker-enabled", havingValue = "true")
public class SampleProductionWorker {
    /** 通过短事务操作批次租约、调用账本及结算检查点。 */
    private final SampleRunRecordService records;
    /** 读取冻结计划和经摘要校验的专家种子。 */
    private final SampleProductionService production;
    /** 独立样本生成端口，不具备调用被测目标的能力。 */
    private final SampleGenerationPort generator;
    /** 固定命令序列化和数据库UTC时间转换。 */
    private final FactoryJsonSupport json;
    /** 专用有限线程池；调度线程不直接等待模型响应。 */
    private final TaskExecutor executor;
    /** 本进程待续租索引；数据库租约才是执行权依据，不能用此Map代替持久化恢复。 */
    private final Map<String, AgentSampleRunDO> active = new ConcurrentHashMap<>();

    /** 注入独立生产依赖，明确选择sampleProductionExecutor而非通用线程池。 */
    public SampleProductionWorker(SampleRunRecordService records, SampleProductionService production, SampleGenerationPort generator,
                                  FactoryJsonSupport json, @Qualifier("sampleProductionExecutor") TaskExecutor executor) {
        this.records = records;
        this.production = production;
        this.generator = generator;
        this.json = json;
        this.executor = executor;
    }

    /** 周期扫描可领取批次；投递失败时释放租约，保留数据库任务供以后恢复。 */
    @Scheduled(fixedDelayString = "${eval.agent.sample-factory.recovery-interval-ms:5000}")
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

    /** 每5秒续租；续租失败不等于远端请求被取消，失去执行权的线程不能继续推进批次。 */
    @Scheduled(fixedDelay = 5000L)
    public void heartbeat() {
        active.forEach((worker, run) -> {
            try {
                if (!records.heartbeat(run.getRunId(), worker)) active.remove(worker);
            } catch (RuntimeException ex) {
                safeLog("生产租约续租失败", run, ex);
            }
        });
    }

    /** 按是否存在在途操作收口异常，最终总是尝试移除心跳索引和释放租约。 */
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

    /**
     * 从数据库读取最新检查点并推进，不能只依赖领取时的original快照。
     * 已确认操作仅结算；ACCEPTED可以首次发送；CALLING/UNKNOWN保守阻塞，禁止自动重发。
     */
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
                // 专家输入及上一轮正文作为JSON资料传入，不授予其修改系统提示词或调用工具的权限。
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
                // 发出请求前预留输入估算及最大输出；响应确认后结算用量，估算不是精确计费。
                long reservation = TokenEstimator.estimate(command.systemPrompt()) + TokenEstimator.estimate(command.userPayloadJson()) + profile.maxOutputTokens();
                if (!records.prepare(run.getRunId(), worker, command, reservation)) return;
                run = records.authorized(run.getTenantId(), run.getActorId(), run.getRunId());
            }
            var op = records.pending(run);
            if ("SUCCEEDED".equals(op.getStatus())) {
                // 包括原线程失租后的迟到响应：当前Worker只结算已存事实，不再调用模型。
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
                // 网络调用在数据库事务外，响应事实落库后下一次循环才进行质量结算。
                records.recordResponse(op.getOperationId(), worker, generator.generate(command));
            } catch (RuntimeException ex) {
                records.recordUnknown(op.getOperationId(), worker);
                records.block(run.getRunId(), worker);
                safeLog("生成操作结果未确认", run, ex);
                return;
            }
        }
    }

    /** 释放失败只记录安全元数据，不能为了清理执行权而覆盖原业务事实。 */
    private void release(AgentSampleRunDO run, String worker) {
        try {
            records.release(run.getRunId(), worker);
        } catch (RuntimeException ex) {
            safeLog("生产释放租约失败", run, ex);
        }
    }

    /** 只记录批次ID和异常类型，不输出可能含种子或模型原文的异常消息。 */
    private void safeLog(String reason, AgentSampleRunDO run, RuntimeException ex) {
        log.warn("D05{}，runId={}, errorType={}", reason, run.getRunId(), ex.getClass().getSimpleName());
    }
}
