package com.kant.llm.eval.agent.production.worker;

import com.kant.llm.eval.agent.production.dao.entity.AgentSampleRunDO;
import com.kant.llm.eval.agent.production.service.SampleReflectionRunner;
import com.kant.llm.eval.agent.production.service.SampleRunRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后台样本生产执行器，需显式开启worker-enabled；按数据库检查点串行推进一个批次。
 * <p>只负责dispatch投递、heartbeat续租和异常收口；生成/反思由SampleReflectionRunner执行。
 * Worker不再组织提示词、不判断改写次数、不直接调用模型，避免两套反思循环。</p>
 * <p>不接入被测目标；人工审核独立于生成执行器，不占线程等待。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "eval.agent.sample-factory.worker-enabled", havingValue = "true")
public class SampleProductionWorker {
    /** 通过短事务操作批次租约、调用账本及结算检查点。 */
    private final SampleRunRecordService records;
    /** 可恢复Reflection执行适配，负责逐步执行生成与评审，不在内存隐藏调用。 */
    private final SampleReflectionRunner runner;
    /** 专用有限线程池；调度线程不直接等待模型响应。 */
    private final TaskExecutor executor;
    /** 本进程待续租索引；数据库租约才是执行权依据，不能用此Map代替持久化恢复。 */
    private final Map<String, AgentSampleRunDO> active = new ConcurrentHashMap<>();

    /** 注入独立生产依赖，明确选择sampleProductionExecutor而非通用线程池。 */
    public SampleProductionWorker(SampleRunRecordService records, SampleReflectionRunner runner,
                                  @Qualifier("sampleProductionExecutor") TaskExecutor executor) {
        this.records = records;
        this.runner = runner;
        this.executor = executor;
    }

    /** 周期扫描可领取批次；投递失败时释放租约，保留数据库任务供以后恢复。 */
    @Scheduled(fixedDelayString = "${eval.agent.sample-factory.recovery-interval-ms:5000}")
    public void dispatch() {
        // 扫描结果只是候选任务；claim成功后才获得数据库执行权，线程池满载则把任务留给下一轮扫描。
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
        // 心跳与执行线程解耦；active丢失只影响本进程续租，不会删除数据库中的批次和操作事实。
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
        // Runner异常时根据是否存在pending operation决定BLOCKED或FAILED，避免把未知调用错误当成普通失败。
        try {
            runner.advance(original, worker);
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
