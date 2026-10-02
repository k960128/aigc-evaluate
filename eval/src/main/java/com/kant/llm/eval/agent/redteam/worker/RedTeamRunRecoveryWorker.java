package com.kant.llm.eval.agent.redteam.worker;

import com.kant.agent.contract.redteam.RedTeamRun;
import com.kant.agent.port.redteam.RedTeamRunRecorder;
import com.kant.agent.service.redteam.MultiTurnEvaluationOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * D03 持久化运行接管 Worker。
 *
 * <p>HTTP 请求只负责写入 QUEUED 运行，进程重启后也由本 Worker 根据数据库
 * 运行事实继续推进。每个候选运行先抢占短租约，再投递到有限线程池；租约
 * 过期后其他实例才可以重新竞争。多轮编排器在动作边界续租并检查失效状态。</p>
 */
@Slf4j
@Component
public class RedTeamRunRecoveryWorker {

    private static final long LEASE_SECONDS = 30L;

    private final RedTeamRunRecorder runRecorder;
    private final MultiTurnEvaluationOrchestrator orchestrator;
    private final TaskExecutor taskExecutor;
    private final java.util.concurrent.ConcurrentMap<String, RedTeamRun> active = new java.util.concurrent.ConcurrentHashMap<>();

    public RedTeamRunRecoveryWorker(
            RedTeamRunRecorder runRecorder,
            MultiTurnEvaluationOrchestrator orchestrator,
            @Qualifier("redTeamWorkerExecutor") TaskExecutor taskExecutor) {
        this.runRecorder = runRecorder;
        this.orchestrator = orchestrator;
        this.taskExecutor = taskExecutor;
    }

    /** 周期性接管排队或上次进程中断留下的运行。 */
    // @Scheduled(
//            fixedDelayString = "${eval.agent.red-team.recovery-interval-ms:1000}",
//            initialDelayString = "${eval.agent.red-team.recovery-initial-delay-ms:1000}")
    public void dispatchRecoverableRuns() {
        for (RedTeamRun candidate : runRecorder.listRecoverableRuns()) {
            String workerId = newWorkerId();
            if (!runRecorder.tryClaimWorker(candidate, workerId,
                    Instant.now().plusSeconds(LEASE_SECONDS))) {
                continue;
            }
            try {
                active.put(workerId, candidate);
                taskExecutor.execute(() -> runClaimed(candidate, workerId));
            } catch (RuntimeException ex) {
                active.remove(workerId);
                // 线程池拒绝时不能留下一个等待三十秒才释放的僵尸租约。
                runRecorder.releaseWorkerLease(candidate, workerId);
                log.warn("D03运行未能投递到Worker线程池，runId={}, errorType={}",
                        candidate.runId(), ex.getClass().getSimpleName());
            }
        }
    }

    /** 在持有租约的线程中恢复运行，结束后无条件释放本次租约。 */
    private void runClaimed(RedTeamRun candidate, String workerId) {
        RedTeamWorkerContext.bind(candidate, workerId);
        try {
            orchestrator.recover(candidate, workerId);
        } catch (RuntimeException ex) {
            // 编排器会将可分类的失败写入运行事实；这里仅记录稳定类型，
            // 不把输入、响应或第三方凭据写入日志。
            log.warn("D03运行Worker执行结束，runId={}, errorType={}",
                    candidate.runId(), ex.getClass().getSimpleName());
        } finally {
            RedTeamWorkerContext.clear();
            active.remove(workerId);
            runRecorder.releaseWorkerLease(candidate, workerId);
        }
    }

    /** 模型调用可能持续超过30秒，续租不依赖模型调用线程返回。 */
    // @Scheduled(fixedDelay = 5000L)
    public void heartbeat() {
        active.forEach((workerId, run) -> {
            try {
                if (!runRecorder.renewWorkerLease(run, workerId, Instant.now().plusSeconds(LEASE_SECONDS)))
                    active.remove(workerId);
            } catch (RuntimeException ex) {
                log.warn("D03租约续租失败，runId={}, errorType={}", run.runId(), ex.getClass().getSimpleName());
            }
        });
    }

    private String newWorkerId() {
        return "worker_" + UUID.randomUUID().toString().replace("-", "");
    }
}
