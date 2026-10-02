package com.kant.llm.eval.agent.validation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kant.agent.contract.assessment.*;
import com.kant.agent.contract.budget.*;
import com.kant.agent.contract.probe.*;
import com.kant.agent.contract.validation.*;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.dao.entity.AgentFindingChangeDO;
import com.kant.llm.eval.agent.finding.dao.mapper.AgentFindingChangeMapper;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import com.kant.llm.eval.agent.finding.service.FindingRecordService;
import com.kant.llm.eval.agent.redteam.dao.entity.*;
import com.kant.llm.eval.agent.redteam.dao.mapper.*;
import com.kant.llm.eval.agent.validation.dao.entity.*;
import com.kant.llm.eval.agent.validation.dao.mapper.*;
import com.kant.llm.eval.agent.validation.dto.*;
import com.kant.llm.eval.agent.validation.support.D04JsonSupport;
import com.kant.llm.eval.agent.validation.worker.ValidationWorkerContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

/** 验证运行事务边界。仅依赖事实存储，不依赖Probe端口，避免与共享执行适配器形成循环。 */
@Service
public class ValidationRunRecordService {
    private final AgentValidationRunMapper runs;
    private final AgentValidationCommandMapper commands;
    private final AgentValidationAssessmentMapper assessments;
    private final AgentValidationOccurrenceMapper occurrences;
    private final AgentRedTeamSessionMapper sessions;
    private final AgentRedTeamSessionCheckpointMapper checkpoints;
    private final FindingRecordService findings;
    private final AgentFindingChangeMapper changes;
    private final D04JsonSupport json;
    public ValidationRunRecordService(AgentValidationRunMapper runs,AgentValidationCommandMapper commands,
            AgentValidationAssessmentMapper assessments,AgentValidationOccurrenceMapper occurrences,
            AgentRedTeamSessionMapper sessions,AgentRedTeamSessionCheckpointMapper checkpoints,
            FindingRecordService findings,AgentFindingChangeMapper changes,D04JsonSupport json) {
        this.runs=runs; this.commands=commands; this.assessments=assessments; this.occurrences=occurrences;
        this.sessions=sessions; this.checkpoints=checkpoints; this.findings=findings; this.changes=changes; this.json=json;
    }

    /** 创建只保存QUEUED计划；调用者已锁发现根并校验授权引用和目标条件。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public AgentValidationRunDO create(String tenant,String actor,String key,String hash,ValidationPlan plan) {
        var row=new AgentValidationRunDO(); row.setRunId(id("vrun_")); row.setTenantId(tenant); row.setActorId(actor);
        row.setFindingId(plan.findingId()); row.setKind(plan.kind().name()); row.setIdempotencyKey(key); row.setRequestHash(hash);
        row.setPlanJson(json.write(plan)); row.setStatus("QUEUED"); row.setVersion(0L); row.setBudgetJson(json.write(new BudgetLedger(plan.budget()).snapshot()));
        row.setResultsJson("[]"); row.setPendingTokens(0L); row.setCursor(0); row.setStopRequested(false);
        row.setCreatedAt(LocalDateTime.now()); row.setDeadlineAt(row.getCreatedAt().plusSeconds(plan.budget().maxDurationSeconds()));
        runs.insert(row); return row;
    }
    public Optional<AgentValidationRunDO> replay(String tenant,String actor,String root,String kind,String key,String hash) {
        var r=runs.selectOne(new LambdaQueryWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getTenantId,tenant)
                .eq(AgentValidationRunDO::getActorId,actor).eq(AgentValidationRunDO::getFindingId,root)
                .eq(AgentValidationRunDO::getKind,kind).eq(AgentValidationRunDO::getIdempotencyKey,key));
        if(r!=null && !r.getRequestHash().equals(hash)) throw error(AgentFindingErrorCode.IDEMPOTENCY_CONFLICT); return Optional.ofNullable(r);
    }
    public AgentValidationRunDO authorized(String tenant,String actor,String runId) {
        var r=runs.selectOne(new LambdaQueryWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getRunId,runId)
                .eq(AgentValidationRunDO::getTenantId,tenant).eq(AgentValidationRunDO::getActorId,actor));
        if(r==null) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND); return r;
    }
    public List<ValidationResponses.Run> history(String tenant,String actor,String root) {
        findings.loadSource(tenant,actor,root);
        return runs.selectList(new LambdaQueryWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getFindingId,root)
                .eq(AgentValidationRunDO::getTenantId,tenant).eq(AgentValidationRunDO::getActorId,actor).orderByAsc(AgentValidationRunDO::getId))
                .stream().map(this::response).toList();
    }
    public List<ValidationResponses.Occurrence> occurrences(String tenant,String actor,String root) {
        findings.loadSource(tenant,actor,root); findings.requireCanonical(tenant,actor,root);
        return occurrences.selectList(new LambdaQueryWrapper<AgentValidationOccurrenceDO>().eq(AgentValidationOccurrenceDO::getFindingId,root)
                .orderByAsc(AgentValidationOccurrenceDO::getId)).stream().map(o -> new ValidationResponses.Occurrence(o.getOccurrenceId(),o.getFindingId(),
                        o.getRunId(),o.getCaseIndex(),o.getAssessmentId(),o.getTargetSnapshotRef(),json.instant(o.getCreatedAt()))).toList();
    }
    public List<Assessment> assessments(String tenant,String actor,String runId) {
        authorized(tenant,actor,runId);
        return assessments.selectList(new LambdaQueryWrapper<AgentValidationAssessmentDO>().eq(AgentValidationAssessmentDO::getRunId,runId)
                .orderByAsc(AgentValidationAssessmentDO::getId)).stream().map(a -> json.read(a.getAssessmentJson(),Assessment.class)).toList();
    }
    public ValidationPlan plan(AgentValidationRunDO r) { return json.read(r.getPlanJson(),ValidationPlan.class); }
    public List<ValidationStepResult> results(AgentValidationRunDO r) { return json.read(r.getResultsJson(),new TypeReference<List<ValidationStepResult>>() {}); }
    public ValidationResponses.Run response(AgentValidationRunDO r) {
        return new ValidationResponses.Run(r.getRunId(),"REGRESSION".equals(r.getKind())?"STANDARD_EVALUATION":"INDEPENDENT_VERIFICATION",
                r.getStatus(),r.getVersion(),plan(r),json.read(r.getBudgetJson(),BudgetSnapshot.class),results(r),Boolean.TRUE.equals(r.getStopRequested()),
                r.getErrorCode(),r.getErrorSummary(),r.getPendingExecutionId(),json.instant(r.getCreatedAt()),json.instant(r.getDeadlineAt()),json.instant(r.getFinishedAt()));
    }

    /** BLOCKED仅查询迟到事实，不允许重新发出目标请求。 */
    public List<AgentValidationRunDO> recoverable() {
        return runs.selectList(new LambdaQueryWrapper<AgentValidationRunDO>().in(AgentValidationRunDO::getStatus,"QUEUED","RUNNING","BLOCKED")
                .and(q -> q.isNull(AgentValidationRunDO::getLeaseUntil).or().lt(AgentValidationRunDO::getLeaseUntil,LocalDateTime.now()))
                .orderByAsc(AgentValidationRunDO::getLeaseUntil).orderByAsc(AgentValidationRunDO::getId).last("LIMIT 20"));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean claim(String runId,String worker) {
        var row=lock(runId);
        if(!Set.of("QUEUED","RUNNING","BLOCKED").contains(row.getStatus()) || row.getLeaseUntil()!=null && row.getLeaseUntil().isAfter(LocalDateTime.now())) return false;
        var update=new LambdaUpdateWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getId,row.getId())
                .set(AgentValidationRunDO::getWorkerId,worker).set(AgentValidationRunDO::getLeaseUntil,LocalDateTime.now().plusSeconds(30));
        // 技术租约续接不制造业务版本抖动，只有首次QUEUED→RUNNING推进业务版本。
        if("QUEUED".equals(row.getStatus())) update.set(AgentValidationRunDO::getStatus,"RUNNING").setSql("version = version + 1");
        return runs.update(null,update)==1;
    }
    public boolean heartbeat(String runId,String worker) {
        return runs.update(null,new LambdaUpdateWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getRunId,runId)
                .eq(AgentValidationRunDO::getWorkerId,worker).gt(AgentValidationRunDO::getLeaseUntil,LocalDateTime.now())
                .in(AgentValidationRunDO::getStatus,"RUNNING","BLOCKED").set(AgentValidationRunDO::getLeaseUntil,LocalDateTime.now().plusSeconds(30)))==1;
    }
    public void release(String runId,String worker) {
        var current=runs.selectOne(new LambdaQueryWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getRunId,runId)
                .eq(AgentValidationRunDO::getWorkerId,worker));
        if(current==null) return;
        runs.update(null,new LambdaUpdateWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getRunId,runId)
                .eq(AgentValidationRunDO::getWorkerId,worker).set(AgentValidationRunDO::getWorkerId,null)
                .set(AgentValidationRunDO::getLeaseUntil,"BLOCKED".equals(current.getStatus())?LocalDateTime.now().plusSeconds(30):null));
    }

    /** 预算预留、在途固定命令与新会话在同一短事务提交。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public boolean prepare(String runId,String worker,ProbeCommand command,long tokens) {
        var row=owned(runId,worker); if(row.getPendingCommandJson()!=null) return false;
        if(!"RUNNING".equals(row.getStatus()) || Boolean.TRUE.equals(row.getStopRequested()) || !row.getDeadlineAt().isAfter(LocalDateTime.now())) return false;
        checkFixedCommand(row,command);
        var location=location(plan(row),row.getCursor()); var ledger=new BudgetLedger(plan(row).budget(),json.read(row.getBudgetJson(),BudgetSnapshot.class));
        if((location.step()==1 && !ledger.reserveAttempt()) || !ledger.reserveTargetCall() || !ledger.reserveInputTokens(tokens)) {
            terminal(row,"FAILED","BUDGET_EXHAUSTED","独立验证预算不足，固定范围未全部执行"); save(row); closeSessions(row,"FAILED"); return false;
        }
        if(command.sessionRef()!=null && location.step()==1) openSession(row,command);
        row.setPendingCommandJson(json.write(command)); row.setPendingTokens(tokens); row.setBudgetJson(json.write(ledger.snapshot())); save(row); return true;
    }

    /** D01首次受理与真正外部调用前均检查：必须是持租约线程的已冻结命令。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireWorkerForProbe(ProbeCommand command) {
        var ctx=ValidationWorkerContext.current();
        if(ctx==null || !ctx.runId().equals(command.context().runId()) || !ctx.tenantId().equals(command.context().tenantId())
                || !ctx.actorId().equals(command.context().actorId())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
        var row=owned(ctx.runId(),ctx.workerId());
        if(!"RUNNING".equals(row.getStatus()) || Boolean.TRUE.equals(row.getStopRequested()) || !row.getDeadlineAt().isAfter(LocalDateTime.now())
                || row.getPendingCommandJson()==null || !command.equals(json.read(row.getPendingCommandJson(),ProbeCommand.class)))
            throw error(AgentFindingErrorCode.STATE_CONFLICT);
        if(command.sessionRef()!=null) {
            var session=sessions.selectOne(new LambdaQueryWrapper<AgentRedTeamSessionDO>().eq(AgentRedTeamSessionDO::getSessionId,command.sessionRef()));
            if(session==null || !session.getRunId().equals(row.getRunId()) || !session.getAttemptId().equals(command.context().attemptId())
                    || !session.getTenantId().equals(ctx.tenantId()) || !session.getActorId().equals(ctx.actorId())
                    || !session.getTargetSnapshotRef().equals(plan(row).targetSnapshotRef()) || !"ACTIVE".equals(session.getStatus()))
                throw error(AgentFindingErrorCode.STATE_CONFLICT);
            var cp=checkpoints.selectOne(new LambdaQueryWrapper<AgentRedTeamSessionCheckpointDO>()
                    .eq(AgentRedTeamSessionCheckpointDO::getSessionId,session.getSessionId())
                    .eq(AgentRedTeamSessionCheckpointDO::getCheckpointVersion,session.getCheckpointVersion()));
            if(cp==null || cp.getTurnCount()!=location(plan(row),row.getCursor()).step()-1
                    || !cp.getTurnCount().equals(session.getTurnCount()) || !cp.getHistoryHash().equals(historyHash(row,session)))
                throw error(AgentFindingErrorCode.EVIDENCE_INSUFFICIENT);
        }
    }

    /** 完成新判定、预算结算、会话检查点和复发记录原子提交，旧Worker不得落盘。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public void complete(String runId,String worker,TargetObservation observation,Assessment assessment) {
        var row=owned(runId,worker); var command=json.read(row.getPendingCommandJson(),ProbeCommand.class);
        if(!observation.context().equals(command.context()) || !observation.idempotencyKey().equals(command.idempotencyKey())
                || !observation.reference().probeId().equals(command.probeId()) || observation.executionMode()!=command.executionMode()
                || !observation.targetSnapshotRef().equals(plan(row).targetSnapshotRef()) || !observation.target().equals(command.target())
                || !observation.riskId().equals(command.riskId()) || !observation.ruleSetVersion().equals(command.ruleSetVersion())
                || !Objects.equals(observation.sessionRef(),command.sessionRef())) throw error(AgentFindingErrorCode.EVIDENCE_INSUFFICIENT);
        if(observation.status()!=ProbeExecutionStatus.SUCCEEDED && observation.status()!=ProbeExecutionStatus.FAILED
                && observation.status()!=ProbeExecutionStatus.CANCELLED) throw error(AgentFindingErrorCode.STATE_CONFLICT);
        var ledger=new BudgetLedger(plan(row).budget(),json.read(row.getBudgetJson(),BudgetSnapshot.class)); var usage=observation.usage();
        boolean withinCalls=ledger.settleKnownTargetCalls(1,usage.targetCalls()); boolean withinTokens=true; boolean measured=true;
        if(usage.targetCalls()==0) ledger.releaseReservedInputTokens(row.getPendingTokens());
        else if(usage.inputTokens()!=null && usage.outputTokens()!=null)
            withinTokens=ledger.settleKnownTargetTokens(row.getPendingTokens(),usage.inputTokens(),usage.outputTokens());
        else { measured=false; ledger.settleUnknownTargetTokens(row.getPendingTokens()); }
        var position=location(plan(row),row.getCursor()); var all=new ArrayList<>(results(row));
        // 步骤索引只引用本轮物理执行，完整累计轨迹由Assessment单独保存。
        List<String> currentRefs=new ArrayList<>(); currentRefs.add(observation.inputEvidence().evidenceRef());
        if(observation.responseEvidence()!=null) currentRefs.add(observation.responseEvidence().evidenceRef());
        if(observation.errorEvidence()!=null) currentRefs.add(observation.errorEvidence().evidenceRef());
        all.add(new ValidationStepResult(position.caseIndex(),position.step(),observation.reference().executionId(),assessment,currentRefs));
        var ar=new AgentValidationAssessmentDO(); ar.setAssessmentId(assessment.assessmentId()); ar.setRunId(runId); ar.setAssessmentJson(json.write(assessment)); assessments.insert(ar);
        row.setResultsJson(json.write(all)); row.setBudgetJson(json.write(ledger.snapshot())); row.setCursor(row.getCursor()+1);
        row.setPendingCommandJson(null); row.setPendingTokens(0L); row.setPendingExecutionId(null);
        if(assessment.verdict()==AssessmentVerdict.RISK_OBSERVED) appendOccurrence(row,assessment,position.caseIndex());
        if(command.sessionRef()!=null) advanceSession(row,command,observation,position);
        if(observation.status()!=ProbeExecutionStatus.SUCCEEDED) terminal(row,"FAILED","TARGET_FAILED","目标执行失败或调用前取消，不能作为修复依据");
        else if(!withinCalls || !withinTokens || !measured) terminal(row,"FAILED","BUDGET_NOT_VERIFIABLE","预算超限或计量不足，禁止后续调用");
        else if(assessment.verdict()==AssessmentVerdict.INCONCLUSIVE) terminal(row,"FAILED","EVIDENCE_INCONCLUSIVE","证据不足或空响应，不能作为修复依据");
        else if(Boolean.TRUE.equals(row.getStopRequested())) terminal(row,"STOPPED","STOP_REQUESTED","已保存迟到执行事实，不再新增调用");
        else if(row.getCursor()==totalSteps(plan(row))) terminal(row,"COMPLETED",null,null);
        else if(!row.getDeadlineAt().isAfter(LocalDateTime.now())) terminal(row,"FAILED","DEADLINE_EXPIRED","固定验证超过运行期限");
        else { row.setStatus("RUNNING"); row.setErrorCode(null); row.setErrorSummary(null); }
        save(row); if(row.getFinishedAt()!=null) closeSessions(row,"COMPLETED".equals(row.getStatus())?"COMPLETED":"FAILED");
    }

    /** 未知结果保留在途命令和预算，不自动重发或释放；只允许核对迟到事实。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void block(String runId,String worker,String executionId) {
        var row=owned(runId,worker);
        if("BLOCKED".equals(row.getStatus()) && Objects.equals(row.getPendingExecutionId(),executionId)) return;
        row.setStatus("BLOCKED"); row.setPendingExecutionId(executionId); row.setErrorCode("EXECUTION_UNCONFIRMED");
        row.setErrorSummary("已受理操作结果未确认；禁止自动重发，预算预留保留"); save(row);
    }
    /** 无在途调用时可以安全停止/超时；意图落盘后才阻止下一动作。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void endWithoutPending(String runId,String worker,String status,String code,String summary) {
        var row=owned(runId,worker); if(row.getPendingCommandJson()!=null) throw error(AgentFindingErrorCode.STATE_CONFLICT);
        terminal(row,status,code,summary); save(row); closeSessions(row,"STOPPED".equals(status)?"STOPPED":"FAILED");
    }
    /** 已预留但确认尚未受理的动作，在停止/超时时按未发请求释放额度。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void abandonUnaccepted(String runId,String worker) {
        var row=owned(runId,worker); if(row.getPendingCommandJson()==null) return;
        var ledger=new BudgetLedger(plan(row).budget(),json.read(row.getBudgetJson(),BudgetSnapshot.class));
        ledger.settleKnownTargetCalls(1,0); ledger.releaseReservedInputTokens(row.getPendingTokens());
        row.setBudgetJson(json.write(ledger.snapshot())); row.setPendingCommandJson(null); row.setPendingTokens(0L);
        terminal(row,Boolean.TRUE.equals(row.getStopRequested())?"STOPPED":"FAILED","NOT_SUBMITTED","已停止或超时，固定动作确认未受理");
        save(row); closeSessions(row,"STOPPED");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ValidationResponses.Stop stop(String tenant,String actor,String runId,ValidationRequests.Stop request) {
        authorized(tenant,actor,runId); var row=lock(runId); String hash=json.hash(request);
        var old=commands.selectOne(new LambdaQueryWrapper<AgentValidationCommandDO>().eq(AgentValidationCommandDO::getRunId,runId)
                .eq(AgentValidationCommandDO::getIdempotencyKey,request.idempotencyKey()));
        if(old!=null) { if(!hash.equals(old.getRequestHash())) throw error(AgentFindingErrorCode.IDEMPOTENCY_CONFLICT);
            return json.read(old.getResultJson(),ValidationResponses.Stop.class); }
        if(row.getVersion()!=request.expectedVersion().longValue()) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        if(!Set.of("QUEUED","RUNNING","BLOCKED").contains(row.getStatus())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
        row.setStopRequested(true);
        if(row.getPendingCommandJson()==null) { terminal(row,"STOPPED","STOP_REQUESTED","用户停止固定验证"); closeSessions(row,"STOPPED"); }
        save(row); var result=new ValidationResponses.Stop(runId,row.getVersion(),true);
        var audit=new AgentValidationCommandDO(); audit.setRunId(runId); audit.setIdempotencyKey(request.idempotencyKey()); audit.setRequestHash(hash);
        audit.setResultJson(json.write(result)); commands.insert(audit); return result;
    }

    /** 每次命中追加独立发生；只有已确认发现的固定回归才自动重新打开整改。 */
    private void appendOccurrence(AgentValidationRunDO row,Assessment assessment,int caseIndex) {
        if(occurrences.selectCount(new LambdaQueryWrapper<AgentValidationOccurrenceDO>().eq(AgentValidationOccurrenceDO::getRunId,row.getRunId())
                .eq(AgentValidationOccurrenceDO::getCaseIndex,caseIndex))>0) return;
        var hit=new AgentValidationOccurrenceDO(); hit.setOccurrenceId(id("vocc_")); hit.setFindingId(row.getFindingId()); hit.setRunId(row.getRunId());
        hit.setCaseIndex(caseIndex);
        hit.setAssessmentId(assessment.assessmentId()); hit.setTargetSnapshotRef(plan(row).targetSnapshotRef()); hit.setCreatedAt(LocalDateTime.now()); occurrences.insert(hit);
        if(!"REGRESSION".equals(row.getKind())) return;
        var state=findings.lockCurrent(row.getTenantId(),row.getActorId(),row.getFindingId());
        if(!"CONFIRMED".equals(state.getVerificationStatus())) return;
        state.setRemediationStatus("REOPENED"); RegressionCaseService.clearProof(state); findings.saveState(state);
        var change=new AgentFindingChangeDO(); change.setChangeId(id("change_")); change.setFindingId(row.getFindingId());
        change.setIdempotencyKey("system:"+hit.getOccurrenceId()); change.setRequestHash(json.hash(hit.getOccurrenceId()));
        change.setAcceptedVersion(state.getVersion()); change.setType("REGRESSION_HIT"); change.setReason("固定回归再次观察到原规则文本风险信号，旧修复依据失效"); change.setActorId(row.getActorId());
        change.setRequestJson(json.write(Map.of("runId",row.getRunId(),"occurrenceId",hit.getOccurrenceId())));
        change.setCreatedAt(LocalDateTime.now());
        change.setResultJson(json.write(new com.kant.llm.eval.agent.finding.dto.FindingChangeResponse(change.getChangeId(),row.getFindingId(),change.getType(),
                state.getVersion(),"REOPENED",change.getReason(),row.getActorId(),json.instant(change.getCreatedAt()),null))); changes.insert(change);
    }
    private void openSession(AgentValidationRunDO row,ProbeCommand command) {
        var s=new AgentRedTeamSessionDO(); s.setSessionId(command.sessionRef()); s.setTaskId(command.context().taskId()); s.setRunId(row.getRunId());
        s.setAttemptId(command.context().attemptId()); s.setTenantId(row.getTenantId()); s.setActorId(row.getActorId()); s.setTargetSnapshotRef(plan(row).targetSnapshotRef());
        s.setStatus("ACTIVE"); s.setTurnCount(0); s.setNextSequence(1); s.setPauseRequested(false); s.setStopRequested(false); s.setCheckpointVersion(0L);
        s.setCreatedAt(LocalDateTime.now()); s.setUpdatedAt(s.getCreatedAt()); s.setDeleted(false); sessions.insert(s); checkpoint(row,s);
    }
    private void advanceSession(AgentValidationRunDO row,ProbeCommand command,TargetObservation o,Position position) {
        var s=sessions.selectOne(new LambdaQueryWrapper<AgentRedTeamSessionDO>().eq(AgentRedTeamSessionDO::getSessionId,command.sessionRef()));
        if(o.status()==ProbeExecutionStatus.SUCCEEDED) { s.setTurnCount(position.step()); s.setNextSequence(position.step()+1); }
        s.setCheckpointVersion(s.getCheckpointVersion()+1); s.setLastProbeId(o.reference().probeId()); s.setLastExecutionId(o.reference().executionId());
        if(o.status()!=ProbeExecutionStatus.SUCCEEDED) s.setStatus("FAILED");
        else if(position.step()==position.binding().definition().steps().size()) { s.setStatus("COMPLETED"); s.setClosedAt(LocalDateTime.now()); }
        s.setUpdatedAt(LocalDateTime.now()); sessions.updateById(s); checkpoint(row,s);
    }
    private void checkpoint(AgentValidationRunDO row,AgentRedTeamSessionDO s) {
        var cp=new AgentRedTeamSessionCheckpointDO(); cp.setCheckpointId(id("vcp_")); cp.setSessionId(s.getSessionId()); cp.setTaskId(s.getTaskId());
        cp.setRunId(s.getRunId()); cp.setAttemptId(s.getAttemptId()); cp.setCheckpointVersion(s.getCheckpointVersion()); cp.setTurnCount(s.getTurnCount());
        cp.setNextSequence(s.getNextSequence()); cp.setLastProbeId(s.getLastProbeId()); cp.setLastExecutionId(s.getLastExecutionId());
        cp.setHistoryHash(historyHash(row,s)); cp.setStatus(s.getStatus()); cp.setCreatedAt(LocalDateTime.now()); cp.setDeleted(false); checkpoints.insert(cp);
    }
    private void closeSessions(AgentValidationRunDO row,String status) {
        for(var s:sessions.selectList(new LambdaQueryWrapper<AgentRedTeamSessionDO>().eq(AgentRedTeamSessionDO::getRunId,row.getRunId()).eq(AgentRedTeamSessionDO::getStatus,"ACTIVE"))) {
            s.setStatus(status); s.setStopRequested(Boolean.TRUE.equals(row.getStopRequested())); s.setClosedAt(LocalDateTime.now()); s.setUpdatedAt(LocalDateTime.now());
            s.setCheckpointVersion(s.getCheckpointVersion()+1); sessions.updateById(s); checkpoint(row,s);
        }
    }
    public record Position(int caseIndex,int step,ValidationPlan.CaseBinding binding) {}
    public Position location(ValidationPlan plan,int cursor) {
        int offset=0; for(int i=0;i<plan.cases().size();i++) { var c=plan.cases().get(i); int size=c.definition().steps().size();
            if(cursor<offset+size) return new Position(i,cursor-offset+1,c); offset+=size; }
        throw error(AgentFindingErrorCode.STATE_CONFLICT);
    }
    public int totalSteps(ValidationPlan p) { return p.cases().stream().mapToInt(c -> c.definition().steps().size()).sum(); }
    /** D04检查点摘要冻结本用例已归档执行、判定及证据索引，不用轮数替代轨迹。 */
    private String historyHash(AgentValidationRunDO row,AgentRedTeamSessionDO session) {
        return json.hash(results(row).stream().filter(r -> session.getAttemptId().equals("vatt_"+json.digest(row.getRunId()+":"+r.caseIndex()).substring(0,32))).toList());
    }
    private void checkFixedCommand(AgentValidationRunDO row,ProbeCommand c) {
        var p=plan(row); var position=location(p,row.getCursor()); var d=position.binding().definition();
        String suffix=json.digest(row.getRunId()+":"+position.caseIndex()).substring(0,32);
        if(!c.context().tenantId().equals(row.getTenantId()) || !c.context().actorId().equals(row.getActorId())
                || !c.context().runId().equals(row.getRunId()) || !c.context().taskId().equals(p.taskId())
                || !c.context().activityId().equals(p.activityId()) || !c.context().authorizationRef().equals(p.authorizationRef())
                || !c.context().attemptId().equals("vatt_"+suffix) || !c.target().equals(d.target())
                || !c.expectedTargetSnapshotRef().equals(p.targetSnapshotRef()) || !c.riskId().equals(d.riskId()) || !c.ruleSetVersion().equals(d.ruleSetVersion())
                || c.executionMode()!=(d.conversation()?ExecutionMode.ADAPTIVE_MULTI_TURN:ExecutionMode.STATIC_SINGLE_TURN)
                || !Objects.equals(c.sessionRef(),d.conversation()?"vsess_"+suffix:null)
                || !c.idempotencyKey().equals("validation:"+row.getRunId()+":"+row.getCursor())
                || !json.digest(c.input()).equals(d.steps().get(position.step()-1).inputHash())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
    }
    private AgentValidationRunDO lock(String runId) {
        var row=runs.selectOne(new LambdaQueryWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getRunId,runId).last("LIMIT 1 FOR UPDATE"));
        if(row==null) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND); return row;
    }
    private AgentValidationRunDO owned(String runId,String worker) {
        var r=lock(runId); if(!worker.equals(r.getWorkerId()) || r.getLeaseUntil()==null || !r.getLeaseUntil().isAfter(LocalDateTime.now()))
            throw error(AgentFindingErrorCode.STATE_CONFLICT); return r;
    }
    private void terminal(AgentValidationRunDO r,String status,String code,String summary) {
        r.setStatus(status); r.setErrorCode(code); r.setErrorSummary(summary); r.setFinishedAt(LocalDateTime.now());
    }
    /** 显式写null，防止清除在途命令/旧错误时受MyBatis默认策略影响。 */
    private void save(AgentValidationRunDO r) {
        long previous=r.getVersion(); r.setVersion(Math.addExact(previous,1));
        int updated=runs.update(null,new LambdaUpdateWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getId,r.getId()).eq(AgentValidationRunDO::getVersion,previous)
                .set(AgentValidationRunDO::getStatus,r.getStatus()).set(AgentValidationRunDO::getVersion,r.getVersion())
                .set(AgentValidationRunDO::getBudgetJson,r.getBudgetJson()).set(AgentValidationRunDO::getResultsJson,r.getResultsJson())
                .set(AgentValidationRunDO::getPendingCommandJson,r.getPendingCommandJson()).set(AgentValidationRunDO::getPendingTokens,r.getPendingTokens())
                .set(AgentValidationRunDO::getPendingExecutionId,r.getPendingExecutionId()).set(AgentValidationRunDO::getCursor,r.getCursor())
                .set(AgentValidationRunDO::getWorkerId,r.getWorkerId()).set(AgentValidationRunDO::getLeaseUntil,r.getLeaseUntil())
                .set(AgentValidationRunDO::getStopRequested,r.getStopRequested()).set(AgentValidationRunDO::getErrorCode,r.getErrorCode())
                .set(AgentValidationRunDO::getErrorSummary,r.getErrorSummary()).set(AgentValidationRunDO::getFinishedAt,r.getFinishedAt()));
        if(updated!=1) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
    }
    private String id(String prefix) { return prefix+UUID.randomUUID().toString().replace("-",""); }
    private ClientException error(AgentFindingErrorCode c) { return new ClientException(c.message(),c); }
}
