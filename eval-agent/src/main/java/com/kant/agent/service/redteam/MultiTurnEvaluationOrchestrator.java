package com.kant.agent.service.redteam;

import com.kant.agent.contract.budget.*;
import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.evidence.*;
import com.kant.agent.contract.probe.*;
import com.kant.agent.contract.redteam.*;
import com.kant.agent.contract.target.*;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.port.redteam.*;
import com.kant.agent.port.target.*;
import com.kant.agent.service.budget.TokenEstimator;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/**
 * D03有界线性多轮编排器。
 * HTTP只创建QUEUED事实；Worker依据数据库租约推进。调用前原子保存操作、动作、
 * 预算，确认响应后原子保存结算和检查点。重启不重新生成在途输入，不重发已受理调用。
 * 首版仍为单风险、单策略、单尝试、单会话；不同运行由有限线程池并行。
 */
public class MultiTurnEvaluationOrchestrator {
    private final RedTeamTaskRepository tasks;
    private final RedTeamRunRecorder records;
    private final RedTeamRunStarter starter;
    private final AdaptiveAttackAgent agent;
    private final AttackActionValidator validator;
    private final ProbeExecutionPort probes;
    private final EvidenceReadPort evidence;
    private final TargetCapabilityPort capabilities;

    public MultiTurnEvaluationOrchestrator(RedTeamTaskRepository tasks, RedTeamRunRecorder records,
            RedTeamRunStarter starter, AdaptiveAttackAgent agent, AttackActionValidator validator,
            ProbeExecutionPort probes, EvidenceReadPort evidence, TargetCapabilityPort capabilities) {
        this.tasks=tasks; this.records=records; this.starter=starter; this.agent=agent;
        this.validator=validator; this.probes=probes; this.evidence=evidence; this.capabilities=capabilities;
    }

    /** 条件抢占任务并持久化排队运行，线程池不可用也不会丢失任务。 */
    public RedTeamRun submit(String tenant, String actor, String taskId, String runId) {
        RedTeamTask task = tasks.find(tenant,actor,taskId).orElseThrow();
        if (task.executionMode()!=ExecutionMode.ADAPTIVE_MULTI_TURN
                || Set.of(RedTeamTaskStatus.RUNNING,RedTeamTaskStatus.PAUSED,RedTeamTaskStatus.BLOCKED).contains(task.status()))
            throw new RedTeamRunStateConflictException("任务模式或状态不能启动多轮运行");
        Instant now=Instant.now();
        RedTeamRun run=new RedTeamRun(runId,taskId,tenant,actor,RedTeamRunStatus.QUEUED,
                new BudgetSnapshot(0,0,0,0,0),0,id("attempt_"),null,"多轮运行已受理",false,now,now,null);
        RedTeamTask running=new RedTeamTask(task.taskId(),task.idempotencyKey(),tenant,actor,task.activityId(),
                task.authorizationRef(),task.target(),task.targetSnapshotRef(),task.riskId(),task.ruleSetVersion(),
                task.executionMode(),task.strategyVersion(),task.seedInput(),task.riskIndicators(),task.budgetPolicy(),
                RedTeamTaskStatus.RUNNING,null,null,task.createdAt(),now,null);
        if (!starter.tryCreateAndClaim(task,running,run)) throw new RedTeamRunStateConflictException("任务已有活动运行");
        return run;
    }

    /** 恢复不改变原截止时间、授权引用、目标和规则版本。 */
    public RedTeamRun resume(String tenant,String actor,String runId) {
        validateResume(tenant, actor, runId);
        records.requestResume(tenant,actor,runId);
        return records.findRun(tenant,actor,runId).orElseThrow();
    }

    /** 仅校验恢复条件；版本化命令在持有运行行锁的事务内再次检查允许状态。 */
    public void validateResume(String tenant,String actor,String runId) {
        RedTeamRun run=records.findRun(tenant,actor,runId).orElseThrow();
        RedTeamTask task=tasks.find(tenant,actor,run.taskId()).orElseThrow();
        AttackSession session=records.findSessionByRun(tenant,actor,runId).orElseThrow();
        if (run.status()!=RedTeamRunStatus.PAUSED || session.status()!=AttackSessionStatus.PAUSED
                || run.stopRequested() || !deadline(task,run).isAfter(Instant.now()))
            throw new RedTeamRunStateConflictException("运行已停止、过期或不是PAUSED状态");
        checkSnapshot(task);
        checkCheckpoint(session,loadHistory(task,run));
    }

    /** 接管后重新读取事实，不采用调度队列里可能过时的运行快照。 */
    public void recover(RedTeamRun candidate,String worker) {
        State state=new State();
        state.run=records.findRun(candidate.tenantId(),candidate.actorId(),candidate.runId()).orElseThrow();
        state.task=tasks.find(state.run.tenantId(),state.run.actorId(),state.run.taskId()).orElseThrow();
        if (state.task.executionMode()!=ExecutionMode.ADAPTIVE_MULTI_TURN) return;
        state.ledger=new BudgetLedger(state.task.budgetPolicy(),state.run.budget());
        state.session=records.findSessionByRun(state.run.tenantId(),state.run.actorId(),state.run.runId()).orElse(null);
        try {
            // 暂停/阻塞后停止不必重新打开目标会话；保留未知调用的预算预留。
            if (state.run.stopRequested()) {
                if (state.session != null) {
                    state.history=loadHistory(state.task,state.run);
                    state.pending=records.findPendingProbe(state.run.tenantId(),state.run.actorId(),state.run.runId()).orElse(null);
                    // 停止只禁止新动作，已确认的在途响应仍保存并结算；未知结果不重发。
                    if (state.pending!=null && !executePending(worker,state)) return;
                } else state.history = null;
                end(worker,state,RedTeamRunStatus.STOPPED,AttackStopReason.USER_STOPPED,"已停止，既有未知执行仍需核对");
                return;
            }
            if (state.run.status()==RedTeamRunStatus.PAUSED) {
                if (!deadline(state.task,state.run).isAfter(Instant.now())) {
                    state.history = loadHistory(state.task,state.run);
                    end(worker,state,RedTeamRunStatus.COMPLETED,AttackStopReason.SESSION_EXPIRED,"暂停期间原运行期限已过");
                }
                return;
            }
            if (state.run.status()==RedTeamRunStatus.BLOCKED) return;
            checkSnapshot(state.task);
            if (state.session==null) {
                if (state.run.actionCount()!=0 || state.run.budget().targetCalls()!=0)
                    throw new IllegalStateException("存在执行事实但缺少会话");
                if (state.ledger.snapshot().attempts()==0) state.ledger.reserveAttempt();
                Instant now=Instant.now();
                state.session=new AttackSession("session_"+hash(state.run.runId()).substring(0,32),
                        state.task.taskId(),state.run.runId(),state.run.currentAttemptId(),state.run.tenantId(),
                        state.run.actorId(),state.task.targetSnapshotRef(),AttackSessionStatus.ACTIVE,0,1,null,null,
                        false,false,0,now,now,null,null,null);
                state.run=copyRun(state.run,RedTeamRunStatus.RUNNING,state.ledger.snapshot(),0,null,null);
                commit(worker,state,checkpoint(state),null,null,null);
            }
            state.history=loadHistory(state.task,state.run);
            checkCheckpoint(state.session,state.history);
            state.pending=records.findPendingProbe(state.run.tenantId(),state.run.actorId(),state.run.runId()).orElse(null);
            if (state.pending==null && state.run.budget().reservedTokens()!=0)
                throw new IllegalStateException("存在预算预留但缺少操作日志");
            while (true) {
                if (!records.renewWorkerLease(state.run,worker,Instant.now().plusSeconds(30)))
                    throw new WorkerLeaseLostException();
                state.run=records.findRun(state.run.tenantId(),state.run.actorId(),state.run.runId()).orElseThrow();
                if (state.pending!=null) { if (!executePending(worker,state)) return; continue; }
                if (state.run.stopRequested()) { end(worker,state,RedTeamRunStatus.STOPPED,AttackStopReason.USER_STOPPED,"用户已停止"); return; }
                // 响应已结算而终态尚未落库时崩溃，恢复仍遵循原来的停止结论。
                if (!records.listFindings(state.run.tenantId(),state.run.actorId(),state.run.runId()).isEmpty()) {
                    end(worker,state,RedTeamRunStatus.COMPLETED,AttackStopReason.RISK_OBSERVED,"既有疑似发现等待独立复核"); return;
                }
                RedTeamActionRecord lastAction = records.listActions(state.run.tenantId(),state.run.actorId(),state.run.runId())
                        .stream().reduce((first,second) -> second).orElse(null);
                if (lastAction != null && "EXECUTION_FAILED".equals(lastAction.actionStatus())) {
                    end(worker,state,RedTeamRunStatus.FAILED,AttackStopReason.TARGET_EXECUTION_FAILED,"既有探测明确失败"); return;
                }
                if (lastAction != null && "REJECTED".equals(lastAction.actionStatus())) {
                    end(worker,state,RedTeamRunStatus.FAILED,AttackStopReason.ACTION_REJECTED,"既有动作被校验器拒绝"); return;
                }
                if (lastAction != null && "ENDED".equals(lastAction.actionStatus())) {
                    end(worker,state,RedTeamRunStatus.COMPLETED,lastAction.suggestedStopReason(),lastAction.reasonSummary()); return;
                }
                if (!deadline(state.task,state.run).isAfter(Instant.now())) {
                    end(worker,state,RedTeamRunStatus.COMPLETED,AttackStopReason.SESSION_EXPIRED,"原运行期限已过"); return;
                }
                if (records.isPauseRequested(state.run.tenantId(),state.run.actorId(),state.run.runId())) {
                    end(worker,state,RedTeamRunStatus.PAUSED,AttackStopReason.USER_PAUSED,"检查点已保存，等待恢复"); return;
                }
                if (state.history.size()>=state.task.budgetPolicy().maxTurnsPerSession()
                        || state.ledger.snapshot().targetCalls()>=state.task.budgetPolicy().maxTargetCalls()) {
                    end(worker,state,RedTeamRunStatus.COMPLETED,AttackStopReason.BUDGET_EXHAUSTED,"达到轮数或调用上限"); return;
                }
                AdaptiveAttackContext context=new AdaptiveAttackContext(state.task.taskId(),state.run.runId(),
                        state.run.currentAttemptId(),state.task.targetSnapshotRef(),state.task.riskId(),
                        state.task.strategyVersion(),state.task.seedInput(),state.history,
                        state.task.budgetPolicy().maxTargetCalls()-state.ledger.snapshot().targetCalls(),
                        state.task.budgetPolicy().maxAgentCalls()-state.ledger.snapshot().agentCalls(),repeated(state.history),false);
                long planned=agent.plannedAgentCalls(context);
                if (!state.ledger.reserveAgentCalls(planned)) {
                    end(worker,state,RedTeamRunStatus.COMPLETED,AttackStopReason.BUDGET_EXHAUSTED,"Agent预算不足"); return;
                }
                state.run=copyRun(state.run,RedTeamRunStatus.RUNNING,state.ledger.snapshot(),state.run.actionCount(),null,null);
                commit(worker,state,null,null,null,null);
                AttackDecision decision=agent.decide(context);
                if (decision==null || decision.status()==AttackDecisionStatus.GENERATION_FAILED || decision.agentCalls()>planned) {
                    end(worker,state,RedTeamRunStatus.FAILED,AttackStopReason.GENERATION_FAILED,"攻击生成失败或调用超过预留"); return;
                }
                AttackAction action=decision.action();
                try { validator.validate(action,context); }
                catch (IllegalArgumentException ex) {
                    int sequence=state.run.actionCount()+1;
                    state.run=copyRun(state.run,state.run.status(),state.ledger.snapshot(),sequence,null,null);
                    // 保留拒绝事实，不采纳Agent给出的范围和任意停止结论。
                    RedTeamActionRecord rejected=new RedTeamActionRecord(id("action_"),state.task.taskId(),state.run.runId(),
                            state.run.currentAttemptId(),sequence,action.actionType(),state.task.strategyVersion(),
                            action.input()==null?null:hash(action.input()),"动作未通过范围、引用或重复输入校验",
                            List.of(),action.suggestedStopReason(),AttackStopReason.ACTION_REJECTED,
                            null,null,null,null,null,null,false,"REJECTED",Instant.now());
                    commit(worker,state,null,rejected,null,null);
                    end(worker,state,RedTeamRunStatus.FAILED,AttackStopReason.ACTION_REJECTED,"动作未通过校验"); return;
                }
                if (action.actionType()==AttackActionType.END_ATTEMPT) {
                    int sequence=state.run.actionCount()+1;
                    state.run=copyRun(state.run,state.run.status(),state.ledger.snapshot(),sequence,null,null);
                    RedTeamActionRecord ended=new RedTeamActionRecord(id("action_"),state.task.taskId(),state.run.runId(),
                            state.run.currentAttemptId(),sequence,action.actionType(),action.strategyVersion(),null,
                            action.reasonSummary(),action.observationRefs(),action.suggestedStopReason(),action.suggestedStopReason(),
                            null,null,null,null,null,null,false,"ENDED",Instant.now());
                    commit(worker,state,null,ended,null,null);
                    end(worker,state,RedTeamRunStatus.COMPLETED,action.suggestedStopReason(),action.reasonSummary()); return;
                }
                long tokens=TokenEstimator.estimate(action.input());
                for (AttackHistoryItem item:state.history) tokens=Math.addExact(tokens,
                        TokenEstimator.estimate(item.input())+TokenEstimator.estimate(item.response()));
                if (!state.ledger.reserveInputTokens(tokens)) {
                    end(worker,state,RedTeamRunStatus.COMPLETED,AttackStopReason.BUDGET_EXHAUSTED,"完整上下文Token预算不足"); return;
                }
                state.ledger.reserveTargetCall();
                int sequence=state.run.actionCount()+1;
                ProbeCommand command=new ProbeCommand(state.run.runId()+":d03:"+sequence,context(state),
                        "probe_"+hash(state.run.runId()+":"+sequence).substring(0,58),state.task.target(),
                        state.task.targetSnapshotRef(),state.task.riskId(),state.task.ruleSetVersion(),
                        ExecutionMode.ADAPTIVE_MULTI_TURN,state.session.sessionId(),action.input(),
                        Set.of(TargetCapability.MULTI_TURN),deadline(state.task,state.run));
                state.pending=new PendingProbe(sequence,"action_"+hash(command.idempotencyKey()),action,command,tokens,"PREPARED");
                state.run=copyRun(state.run,RedTeamRunStatus.RUNNING,state.ledger.snapshot(),sequence,null,null);
                commit(worker,state,null,audit(state,null,false,"PROPOSED"),null,state.pending);
            }
        } catch (WorkerLeaseLostException ex) { throw ex; }
        catch (RuntimeException ex) {
            // 故障后不覆盖已保存的终态，也不能把证据校验失败变成安全结论。
            state.run=records.findRun(state.run.tenantId(),state.run.actorId(),state.run.runId()).orElseThrow();
            if (Set.of(RedTeamRunStatus.COMPLETED,RedTeamRunStatus.FAILED,RedTeamRunStatus.STOPPED,
                    RedTeamRunStatus.PAUSED,RedTeamRunStatus.BLOCKED).contains(state.run.status())) return;
            state.ledger=new BudgetLedger(state.task.budgetPolicy(),state.run.budget());
            state.session=records.findSessionByRun(state.run.tenantId(),state.run.actorId(),state.run.runId()).orElse(state.session);
            // 暂停恰好发生在D01受理前：尚无物理执行时可以取消固定操作，不能误记为UNKNOWN。
            state.pending=records.findPendingProbe(state.run.tenantId(),state.run.actorId(),state.run.runId()).orElse(null);
            if (state.run.status()==RedTeamRunStatus.PAUSING && state.session!=null
                    && (state.pending==null || probes.findByIdempotencyKey(state.run.tenantId(),state.run.actorId(),
                    state.pending.command().idempotencyKey()).isEmpty())) {
                try {
                    state.history=loadHistory(state.task,state.run);
                    checkCheckpoint(state.session,state.history);
                    if (state.pending!=null) executePending(worker,state);
                    end(worker,state,RedTeamRunStatus.PAUSED,AttackStopReason.USER_PAUSED,"调用受理前已暂停，检查点保留");
                    return;
                } catch (WorkerLeaseLostException lost) { throw lost; }
                catch (RuntimeException invalidHistory) { /* 证据异常仍走保守阻塞，不伪造可恢复检查点。 */ }
            }
            state.history=null;
            end(worker,state,RedTeamRunStatus.BLOCKED,AttackStopReason.TARGET_RESULT_UNKNOWN,"恢复或证据校验失败，需核对事实");
        }
    }

    /** 在途操作先按原幂等键查询；只在未受理时继续固定命令，已受理绝不重发。 */
    private boolean executePending(String worker,State state) {
        PendingProbe operation=state.pending;
        TargetObservation observation=probes.findByIdempotencyKey(state.run.tenantId(),state.run.actorId(),
                operation.command().idempotencyKey()).orElse(null);
        if (observation==null) {
            if (state.run.stopRequested() || records.isPauseRequested(state.run.tenantId(),state.run.actorId(),state.run.runId())
                    || !deadline(state.task,state.run).isAfter(Instant.now())) {
                state.ledger.releaseReservedInputTokens(operation.reservedTokens());
                state.ledger.settleKnownTargetCalls(1,0);
                state.run=copyRun(state.run,state.run.status(),state.ledger.snapshot(),state.run.actionCount(),null,null);
                commit(worker,state,null,audit(state,null,false,"NOT_SENT"),null,operation.withStatus("SETTLED"));
                state.pending=null; return true;
            }
            operation=operation.withStatus("DISPATCHING"); state.pending=operation;
            commit(worker,state,null,null,null,operation);
            probes.submit(operation.command());
            observation=probes.findByIdempotencyKey(state.run.tenantId(),state.run.actorId(),operation.command().idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("提交后缺少执行事实"));
        }
        validateObservation(state,observation);
        if (observation.status()==ProbeExecutionStatus.UNKNOWN || !observation.status().isTerminal()) {
            end(worker,state,RedTeamRunStatus.BLOCKED,AttackStopReason.TARGET_RESULT_UNKNOWN,"目标结果未确认，禁止自动重发");
            return false;
        }
        boolean within=state.ledger.settleKnownTargetCalls(1,observation.usage().targetCalls());
        boolean complete=observation.usage().targetCalls()==0
                || observation.usage().inputTokens()!=null && observation.usage().outputTokens()!=null;
        if (observation.usage().targetCalls()==0) state.ledger.releaseReservedInputTokens(operation.reservedTokens());
        else if (complete) within &= state.ledger.settleKnownTargetTokens(operation.reservedTokens(),
                observation.usage().inputTokens(),observation.usage().outputTokens());
        else state.ledger.settleUnknownTargetTokens(operation.reservedTokens());
        boolean risk=false;
        if (observation.status()==ProbeExecutionStatus.SUCCEEDED) {
            EvidenceContent input=read(observation,observation.inputEvidence(),EvidenceType.PROBE_INPUT);
            EvidenceContent output=read(observation,observation.responseEvidence(),EvidenceType.TARGET_RESPONSE);
            if (!operation.command().input().equals(input.content())) throw new IllegalStateException("输入证据不一致");
            state.history.add(new AttackHistoryItem(state.history.size()+1,observation.reference().probeId(),
                    observation.reference().executionId(),input.content(),observation.status(),output.content(),
                    output.reference().evidenceRef(),null,null));
            risk=state.task.riskIndicators().stream().anyMatch(value -> output.content().toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT)));
        }
        state.session=copySession(state.session,AttackSessionStatus.ACTIVE,state.history,operation.sequence()+1,null,null);
        state.run=copyRun(state.run,RedTeamRunStatus.RUNNING,state.ledger.snapshot(),operation.sequence(),null,null);
        RedTeamActionRecord action=audit(state,observation,risk,
                observation.status()==ProbeExecutionStatus.SUCCEEDED?"EXECUTED":"EXECUTION_FAILED");
        commit(worker,state,checkpoint(state),action,risk?finding(state,action):null,operation.withStatus("SETTLED"));
        state.pending=null;
        if (state.run.stopRequested()) { end(worker,state,RedTeamRunStatus.STOPPED,AttackStopReason.USER_STOPPED,"当前响应已保存"); return false; }
        if (observation.usage().targetCalls()==0
                && records.isPauseRequested(state.run.tenantId(),state.run.actorId(),state.run.runId())) {
            end(worker,state,RedTeamRunStatus.PAUSED,AttackStopReason.USER_PAUSED,"调用前收到暂停，目标未调用"); return false;
        }
        if (!complete || !within || observation.status()!=ProbeExecutionStatus.SUCCEEDED) {
            end(worker,state,!complete?RedTeamRunStatus.BLOCKED:
                            observation.status()!=ProbeExecutionStatus.SUCCEEDED?RedTeamRunStatus.FAILED:RedTeamRunStatus.COMPLETED,
                    !complete?AttackStopReason.BUDGET_USAGE_UNKNOWN:!within?AttackStopReason.BUDGET_EXHAUSTED:
                            AttackStopReason.TARGET_EXECUTION_FAILED,"结果或预算不允许继续"); return false;
        }
        if (risk) { end(worker,state,RedTeamRunStatus.COMPLETED,AttackStopReason.RISK_OBSERVED,"已产生疑似发现，尚未独立复核"); return false; }
        return true;
    }

    /** 原子保存操作、预算和检查点，外部模型调用不持有事务。 */
    private void commit(String worker,State state,SessionCheckpoint checkpoint,RedTeamActionRecord action,
            SuspectedFinding finding,PendingProbe operation) {
        records.saveProgress(worker,state.run,state.session,checkpoint,
                new RedTeamAttempt(state.run.currentAttemptId(),state.task.taskId(),state.run.runId(),1,state.task.riskId(),
                        state.task.strategyVersion(),state.run.status(),Math.toIntExact(state.run.budget().targetCalls()),
                        state.run.stopReason(),state.run.stopMessage(),state.run.startedAt(),state.run.finishedAt()),action,finding,operation);
    }

    /** 暂停不是结束；未知状态仍保留操作预留和原始执行以便核对迟到响应。 */
    private void end(String worker,State state,RedTeamRunStatus status,AttackStopReason reason,String message) {
        RedTeamRun latest=records.findRun(state.run.tenantId(),state.run.actorId(),state.run.runId()).orElseThrow();
        if (latest.stopRequested()) { status=RedTeamRunStatus.STOPPED; reason=AttackStopReason.USER_STOPPED; }
        state.run=copyRun(latest,status,state.ledger.snapshot(),state.run.actionCount(),reason,message);
        if (state.session!=null) state.session=copySession(state.session,switch(status) {
            case PAUSED -> AttackSessionStatus.PAUSED; case BLOCKED -> AttackSessionStatus.BLOCKED;
            case STOPPED -> AttackSessionStatus.STOPPED; case FAILED -> AttackSessionStatus.FAILED;
            default -> reason==AttackStopReason.SESSION_EXPIRED?AttackSessionStatus.EXPIRED:AttackSessionStatus.COMPLETED;
        },state.history,state.session.nextSequence(),reason.name(),message);
        commit(worker,state,state.session!=null && state.history!=null?checkpoint(state):null,null,null,null);
    }

    /** 只使用已结算成功动作重建上下文；证据缺失或哈希不一致立即阻断。 */
    private List<AttackHistoryItem> loadHistory(RedTeamTask task,RedTeamRun run) {
        List<AttackHistoryItem> result=new ArrayList<>();
        for (RedTeamActionRecord action:records.listActions(run.tenantId(),run.actorId(),run.runId())) {
            if (!"EXECUTED".equals(action.actionStatus())) continue;
            TargetObservation observation=probes.find(new ProbeLookup(run.tenantId(),run.actorId(),action.executionId())).orElseThrow();
            if (observation.status()!=ProbeExecutionStatus.SUCCEEDED || !task.targetSnapshotRef().equals(observation.targetSnapshotRef())
                    || !run.runId().equals(observation.context().runId())
                    || !task.taskId().equals(observation.context().taskId())
                    || !run.currentAttemptId().equals(observation.context().attemptId())
                    || !task.riskId().equals(observation.riskId()) || !task.ruleSetVersion().equals(observation.ruleSetVersion())
                    || observation.executionMode()!=ExecutionMode.ADAPTIVE_MULTI_TURN)
                throw new IllegalStateException("历史执行边界不一致");
            EvidenceContent input=read(observation,observation.inputEvidence(),EvidenceType.PROBE_INPUT);
            EvidenceContent output=read(observation,observation.responseEvidence(),EvidenceType.TARGET_RESPONSE);
            if (!hash(input.content()).equals(action.inputHash())) throw new IllegalStateException("历史输入摘要不一致");
            result.add(new AttackHistoryItem(result.size()+1,observation.reference().probeId(),action.executionId(),input.content(),
                    observation.status(),output.content(),output.reference().evidenceRef(),null,null));
        }
        return result;
    }
    private void checkCheckpoint(AttackSession session,List<AttackHistoryItem> history) {
        SessionCheckpoint cp=records.findLatestCheckpoint(session.tenantId(),session.actorId(),session.sessionId()).orElseThrow();
        if (cp.version()!=session.checkpointVersion() || cp.turnCount()!=history.size() || session.turnCount()!=history.size()
                || cp.nextSequence()!=session.nextSequence() || !cp.runId().equals(session.runId())
                || !cp.attemptId().equals(session.attemptId())
                || !cp.historyHash().equals(historyHash(history))) throw new IllegalStateException("检查点与已确认轨迹不一致");
    }
    private void validateObservation(State state,TargetObservation observation) {
        ProbeCommand command=state.pending.command();
        if (!command.context().equals(observation.context()) || !command.probeId().equals(observation.reference().probeId())
                || !command.idempotencyKey().equals(observation.idempotencyKey()) || !command.target().equals(observation.target())
                || !command.riskId().equals(observation.riskId()) || !command.ruleSetVersion().equals(observation.ruleSetVersion())
                || !command.expectedTargetSnapshotRef().equals(observation.targetSnapshotRef())
                || observation.executionMode()!=ExecutionMode.ADAPTIVE_MULTI_TURN || !command.sessionRef().equals(observation.sessionRef()))
            throw new IllegalStateException("观察越过当前操作边界");
    }
    /** 分析数据不可作为指令；同时校验证据类型、所属执行和原文摘要。 */
    private EvidenceContent read(TargetObservation observation,EvidenceReference ref,EvidenceType type) {
        if (ref==null || ref.type()!=type || !"PROBE_EXECUTION".equals(ref.ownerType())
                || !observation.reference().executionId().equals(ref.ownerRef())) throw new IllegalStateException("证据索引不完整");
        EvidenceContent value=evidence.read(new EvidenceQuery(observation.context().tenantId(),observation.context().actorId(),
                observation.reference().executionId(),ref.evidenceRef())).orElseThrow();
        if (!ref.equals(value.reference()) || !hash(value.content()).equals(ref.contentHash())) throw new IllegalStateException("证据正文校验失败");
        return value;
    }
    private void checkSnapshot(RedTeamTask task) {
        TargetSnapshot snapshot=capabilities.getSnapshot(task.tenantId(),task.actorId(),task.target());
        if (!task.targetSnapshotRef().equals(snapshot.snapshotRef()) || snapshot.status()!=TargetStatus.AVAILABLE
                || !snapshot.capabilities().supports(TargetCapability.MULTI_TURN) || task.authorizationRef().isBlank())
            throw new RedTeamRunStateConflictException("目标快照、能力或授权引用不满足多轮条件");
    }
    private RedTeamActionRecord audit(State s,TargetObservation o,boolean risk,String status) {
        PendingProbe p=s.pending;
        return new RedTeamActionRecord(p.actionId(),s.task.taskId(),s.run.runId(),s.run.currentAttemptId(),p.sequence(),
                p.action().actionType(),s.task.strategyVersion(),hash(p.command().input()),p.action().reasonSummary(),
                p.action().observationRefs(),null,null,o==null || o.inputEvidence()==null?null:o.inputEvidence().evidenceRef(),
                p.command().probeId(),o==null?null:o.reference().executionId(),o==null?null:o.status(),
                o==null || o.responseEvidence()==null?null:o.responseEvidence().evidenceRef(),o==null?null:o.errorCode(),risk,status,Instant.now());
    }
    /** 多轮发现引用完整前置轨迹，不只引用最后一轮回复。 */
    private SuspectedFinding finding(State s,RedTeamActionRecord action) {
        List<String> refs=new ArrayList<>();
        for (AttackHistoryItem item:s.history) {
            TargetObservation o=probes.find(new ProbeLookup(s.run.tenantId(),s.run.actorId(),item.executionId())).orElseThrow();
            refs.add(o.inputEvidence().evidenceRef()); refs.add(o.responseEvidence().evidenceRef());
        }
        return new SuspectedFinding("finding_"+hash(action.actionId()),s.task.taskId(),s.run.runId(),s.run.currentAttemptId(),
                action.actionId(),s.task.riskId(),refs,"会话响应命中字面风险信号，须独立复核","SUSPECTED",Instant.now());
    }
    private SessionCheckpoint checkpoint(State s) {
        return new SessionCheckpoint("checkpoint_"+hash(s.session.sessionId()+":"+s.session.checkpointVersion()),s.session.sessionId(),
                s.task.taskId(),s.run.runId(),s.run.currentAttemptId(),s.session.checkpointVersion(),s.session.turnCount(),
                s.session.nextSequence(),s.session.lastProbeId(),s.session.lastExecutionId(),historyHash(s.history),s.session.status(),Instant.now());
    }
    private AttackSession copySession(AttackSession s,AttackSessionStatus status,List<AttackHistoryItem> history,
            int next,String code,String message) {
        AttackHistoryItem last=history==null || history.isEmpty()?null:history.get(history.size()-1);
        boolean closed=Set.of(AttackSessionStatus.COMPLETED,AttackSessionStatus.STOPPED,AttackSessionStatus.EXPIRED,
                AttackSessionStatus.FAILED,AttackSessionStatus.CLOSED).contains(status);
        return new AttackSession(s.sessionId(),s.taskId(),s.runId(),s.attemptId(),s.tenantId(),s.actorId(),s.targetSnapshotRef(),
                status,history==null?s.turnCount():history.size(),next,last==null?s.lastProbeId():last.probeId(),
                last==null?s.lastExecutionId():last.executionId(),false,s.stopRequested(),
                history==null?s.checkpointVersion():s.checkpointVersion()+1,
                s.createdAt(),Instant.now(),closed?Instant.now():null,code,message);
    }
    private RedTeamRun copyRun(RedTeamRun r,RedTeamRunStatus status,BudgetSnapshot budget,int count,AttackStopReason reason,String message) {
        boolean terminal=Set.of(RedTeamRunStatus.COMPLETED,RedTeamRunStatus.FAILED,RedTeamRunStatus.STOPPED).contains(status);
        return new RedTeamRun(r.runId(),r.taskId(),r.tenantId(),r.actorId(),status,budget,count,r.currentAttemptId(),reason,
                message,r.stopRequested(),r.createdAt(),r.startedAt(),terminal?Instant.now():null);
    }
    private int repeated(List<AttackHistoryItem> h) {
        int n=0; for (int i=h.size()-1;i>0 && Objects.equals(h.get(i).response(),h.get(i-1).response());i--) n++; return n;
    }
    private ExecutionContext context(State s) {
        return new ExecutionContext(s.task.tenantId(),s.task.actorId(),s.task.activityId(),s.task.taskId(),s.run.runId(),
                s.run.currentAttemptId(),id("trace_"),s.task.authorizationRef());
    }
    private Instant deadline(RedTeamTask t,RedTeamRun r) { return r.startedAt().plusSeconds(t.budgetPolicy().maxDurationSeconds()); }
    private String id(String p) { return p+UUID.randomUUID().toString().replace("-",""); }
    private String historyHash(List<AttackHistoryItem> h) {
        StringBuilder b=new StringBuilder();
        for (AttackHistoryItem i:h) b.append(i.sequence()).append(':').append(i.executionId()).append(':')
                .append(hash(i.input())).append(':').append(hash(i.response())).append(';');
        return hash(b.toString());
    }
    private String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256不可用",ex); }
    }
    /** 仅用于本Worker一次推进的工作快照；数据库才是权威事实来源。 */
    private static final class State {
        RedTeamTask task; RedTeamRun run; AttackSession session; BudgetLedger ledger; PendingProbe pending;
        List<AttackHistoryItem> history=new ArrayList<>();
    }
}
