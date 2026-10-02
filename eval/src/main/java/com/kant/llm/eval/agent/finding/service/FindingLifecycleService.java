package com.kant.llm.eval.agent.finding.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.agent.contract.assessment.AssessmentVerdict;
import com.kant.agent.contract.validation.*;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.dao.entity.*;
import com.kant.llm.eval.agent.finding.dao.mapper.*;
import com.kant.llm.eval.agent.finding.dto.*;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.validation.dao.entity.*;
import com.kant.llm.eval.agent.validation.dao.mapper.*;
import com.kant.llm.eval.agent.validation.dto.ValidationRequests;
import com.kant.llm.eval.agent.validation.service.*;
import com.kant.llm.eval.agent.validation.support.D04JsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

/** 人工归并/拆分及整改闭环。负责人只能确认当前指定、完整通过的固定回归。 */
@Service
public class FindingLifecycleService {
    private final FindingRecordService findings;
    private final AgentFindingOccurrenceMapper memberships;
    private final AgentFindingChangeMapper changes;
    private final AgentCaseCandidateMapper candidates;
    private final AgentRegressionCaseMapper cases;
    private final AgentValidationRunMapper validations;
    private final ValidationRunRecordService runs;
    private final ProbePrincipalProvider principals;
    private final D04JsonSupport json;
    public FindingLifecycleService(FindingRecordService findings,AgentFindingOccurrenceMapper memberships,AgentFindingChangeMapper changes,
            AgentCaseCandidateMapper candidates,AgentRegressionCaseMapper cases,AgentValidationRunMapper validations,
            ValidationRunRecordService runs,ProbePrincipalProvider principals,D04JsonSupport json) {
        this.findings=findings; this.memberships=memberships; this.changes=changes; this.candidates=candidates;
        this.cases=cases; this.validations=validations; this.runs=runs; this.principals=principals; this.json=json;
    }

    /** 双根归并采用固定锁顺序，幂等优先于客户端版本；数据库原始事实永不删除。 */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public FindingChangeResponse change(String root,FindingLifecycleRequest request) {
        var p=principals.currentPrincipal(); findings.loadSource(p.tenantId(),p.actorId(),root); String hash=json.hash(request);
        var replay=prior(root,request.idempotencyKey(),hash,p.actorId()); if(replay!=null) return replay;
        validateFields(request);
        AgentFindingStateDO state,other=null;
        if(request.type()==FindingLifecycleRequest.Type.MERGE) {
            String second=request.otherFindingId(); findings.loadSource(p.tenantId(),p.actorId(),second);
            if(root.equals(second)) throw error(AgentFindingErrorCode.INVALID_REVIEW);
            if(root.compareTo(second)<0) { state=findings.lockCurrent(p.tenantId(),p.actorId(),root); other=findings.lockCurrent(p.tenantId(),p.actorId(),second); }
            else { other=findings.lockCurrent(p.tenantId(),p.actorId(),second); state=findings.lockCurrent(p.tenantId(),p.actorId(),root); }
        } else state=findings.lockCurrent(p.tenantId(),p.actorId(),root);
        replay=prior(root,request.idempotencyKey(),hash,p.actorId()); if(replay!=null) return replay;
        if(state.getVersion()!=request.expectedVersion().longValue()) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        switch(request.type()) {
            case MERGE -> {
                if(other.getVersion()!=request.expectedOtherVersion().longValue()) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
                mutableGroup(state); mutableGroup(other);
                var a=findings.loadSource(p.tenantId(),p.actorId(),root).task();
                var b=findings.loadSource(p.tenantId(),p.actorId(),request.otherFindingId()).task();
                if(!a.target().equals(b.target()) || !a.targetSnapshotRef().equals(b.targetSnapshotRef())
                        || !a.riskId().equals(b.riskId()) || !a.ruleSetVersion().equals(b.ruleSetVersion())
                        || a.executionMode()!=b.executionMode() || !a.riskIndicators().equals(b.riskIndicators()))
                    throw error(AgentFindingErrorCode.INVALID_REVIEW);
                noAssets(root); noAssets(request.otherFindingId());
                for(String occurrence:findings.memberIds(p.tenantId(),p.actorId(),request.otherFindingId()))
                    assign(p.tenantId(),p.actorId(),occurrence,root);
                findings.saveState(other);
            }
            case SPLIT -> {
                mutableGroup(state); noAssets(root);
                if(root.equals(request.occurrenceId()) || !findings.memberIds(p.tenantId(),p.actorId(),root).contains(request.occurrenceId()))
                    throw error(AgentFindingErrorCode.INVALID_REVIEW);
                // 映射回原发生ID而非删除映射；旧根历史和原发生事实都保持可追溯。
                assign(p.tenantId(),p.actorId(),request.occurrenceId(),request.occurrenceId());
                var detached=findings.lockCurrent(p.tenantId(),p.actorId(),request.occurrenceId());
                findings.saveState(detached);
            }
            case START_WORK -> {
                owner(state,request.ownerRef(),p.actorId()); requireStatus(state,"OPEN","REOPENED");
                RegressionCaseService.clearProof(state); state.setRemediationStatus("IN_PROGRESS");
            }
            case READY_FOR_RETEST -> {
                owner(state,request.ownerRef(),p.actorId()); requireStatus(state,"OPEN","IN_PROGRESS","READY_FOR_RETEST","REOPENED");
                currentRun(state,request,p.tenantId(),p.actorId()); state.setRemediationStatus("READY_FOR_RETEST");
            }
            case CONFIRM_RESOLVED -> {
                owner(state,request.ownerRef(),p.actorId()); requireStatus(state,"READY_FOR_RETEST");
                var run=currentRun(state,request,p.tenantId(),p.actorId()); validateResolution(state,run);
                state.setRemediationStatus("RESOLVED"); state.setResolvedBy(p.actorId()); state.setResolutionReason(request.reason());
            }
            case REOPEN -> {
                owner(state,request.ownerRef(),p.actorId()); requireStatus(state,"RESOLVED","ACCEPTED_RISK");
                RegressionCaseService.clearProof(state); state.setRemediationStatus("REOPENED");
            }
            case ACCEPT_RISK -> {
                owner(state,request.ownerRef(),p.actorId()); requireStatus(state,"OPEN","IN_PROGRESS","READY_FOR_RETEST","REOPENED");
                RegressionCaseService.clearProof(state); state.setRemediationStatus("ACCEPTED_RISK");
            }
        }
        findings.saveState(state); LocalDateTime now=LocalDateTime.now(); String changeId="change_"+UUID.randomUUID().toString().replace("-","");
        var result=new FindingChangeResponse(changeId,root,request.type().name(),state.getVersion(),state.getRemediationStatus(),request.reason(),p.actorId(),json.instant(now),request);
        var row=new AgentFindingChangeDO(); row.setChangeId(changeId); row.setFindingId(root); row.setIdempotencyKey(request.idempotencyKey()); row.setRequestHash(hash);
        row.setAcceptedVersion(state.getVersion()); row.setRequestJson(json.write(request)); row.setType(request.type().name()); row.setReason(request.reason()); row.setActorId(p.actorId()); row.setResultJson(json.write(result)); row.setCreatedAt(now);
        changes.insert(row); return result;
    }

    /** 原ID历史不改写，归并后仍可分别查询原根的复核和变更历史。 */
    public List<FindingChangeResponse> history(String root,long afterVersion) {
        var p=principals.currentPrincipal(); findings.loadSource(p.tenantId(),p.actorId(),root);
        if(afterVersion<0) throw error(AgentFindingErrorCode.INVALID_REVIEW);
        return changes.selectList(new LambdaQueryWrapper<AgentFindingChangeDO>().eq(AgentFindingChangeDO::getFindingId,root)
                .gt(AgentFindingChangeDO::getAcceptedVersion,afterVersion).orderByAsc(AgentFindingChangeDO::getAcceptedVersion).last("LIMIT 100"))
                .stream().map(r -> json.read(r.getResultJson(),FindingChangeResponse.class)).toList();
    }
    /** 普通读run，不在持有根锁后反向锁run，避免与Worker的run→根顺序形成死锁。 */
    private AgentValidationRunDO currentRun(AgentFindingStateDO state,FindingLifecycleRequest request,String tenant,String actor) {
        if(!Objects.equals(state.getCurrentRegressionRunId(),request.regressionRunId()) || state.getCurrentRegressionRunId()==null
                || !Objects.equals(state.getRepairSnapshotRef(),request.repairSnapshotRef())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
        var r=runs.authorized(tenant,actor,request.regressionRunId()); var p=runs.plan(r);
        if(!r.getFindingId().equals(state.getFindingId()) || p.kind()!=ValidationPlan.Kind.REGRESSION
                || !p.targetSnapshotRef().equals(request.repairSnapshotRef())) throw error(AgentFindingErrorCode.STATE_CONFLICT); return r;
    }
    private void validateResolution(AgentFindingStateDO state,AgentValidationRunDO run) {
        var plan=runs.plan(run); var result=runs.results(run);
        if(!"COMPLETED".equals(run.getStatus()) || Boolean.TRUE.equals(run.getStopRequested()) || run.getPendingCommandJson()!=null
                || run.getCursor()!=runs.totalSteps(plan) || result.size()!=runs.totalSteps(plan)
                || !json.write(plan.cases().stream().map(c -> new ValidationRequests.CaseRef(c.caseId(),c.version())).toList()).equals(state.getRequiredCasesJson()))
            throw error(AgentFindingErrorCode.STATE_CONFLICT);
        for(int i=0;i<result.size();i++) {
            var item=result.get(i); var position=runs.location(plan,i); var d=position.binding().definition(); var a=item.assessment();
            if(item.caseIndex()!=position.caseIndex() || item.stepSequence()!=position.step() || a.verdict()!=AssessmentVerdict.NO_RISK_OBSERVED
                    || !a.riskId().equals(d.riskId()) || !a.ruleSetVersion().equals(d.ruleSetVersion()) || !a.judgeVersion().equals(d.assertionVersion()))
                throw error(AgentFindingErrorCode.STATE_CONFLICT);
        }
    }
    private void noAssets(String root) {
        if(candidates.selectCount(new LambdaQueryWrapper<AgentCaseCandidateDO>().eq(AgentCaseCandidateDO::getFindingId,root))>0
                || cases.selectCount(new LambdaQueryWrapper<AgentRegressionCaseDO>().eq(AgentRegressionCaseDO::getFindingId,root))>0
                || validations.selectCount(new LambdaQueryWrapper<AgentValidationRunDO>().eq(AgentValidationRunDO::getFindingId,root))>0)
            throw error(AgentFindingErrorCode.STATE_CONFLICT);
    }
    private void mutableGroup(AgentFindingStateDO state) {
        if(!Set.of("SUSPECTED","INCONCLUSIVE").contains(state.getVerificationStatus())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
    }
    private void owner(AgentFindingStateDO state,String requested,String actor) {
        if(!"CONFIRMED".equals(state.getVerificationStatus()) || !actor.equals(requested) || !actor.equals(state.getOwnerRef())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
    }
    private void requireStatus(AgentFindingStateDO state,String... allowed) {
        if(!Arrays.asList(allowed).contains(state.getRemediationStatus())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
    }
    private void assign(String tenant,String actor,String occurrence,String root) {
        var row=memberships.selectOne(new LambdaQueryWrapper<AgentFindingOccurrenceDO>().eq(AgentFindingOccurrenceDO::getOccurrenceId,occurrence));
        if(row==null) { row=new AgentFindingOccurrenceDO(); row.setOccurrenceId(occurrence); row.setFindingId(root); row.setTenantId(tenant); row.setActorId(actor); memberships.insert(row); }
        else { if(!tenant.equals(row.getTenantId()) || !actor.equals(row.getActorId())) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND);
            row.setFindingId(root); memberships.updateById(row); }
    }
    private FindingChangeResponse prior(String root,String key,String hash,String actor) {
        var row=changes.selectOne(new LambdaQueryWrapper<AgentFindingChangeDO>().eq(AgentFindingChangeDO::getFindingId,root).eq(AgentFindingChangeDO::getIdempotencyKey,key));
        if(row==null) return null;
        if(!actor.equals(row.getActorId()) || !hash.equals(row.getRequestHash())) throw error(AgentFindingErrorCode.IDEMPOTENCY_CONFLICT);
        return json.read(row.getResultJson(),FindingChangeResponse.class);
    }
    /** 不接收无关字段，避免一个幂等请求被前端解释成多个不同业务动作。 */
    private void validateFields(FindingLifecycleRequest r) {
        boolean merge=r.type()==FindingLifecycleRequest.Type.MERGE,split=r.type()==FindingLifecycleRequest.Type.SPLIT;
        boolean regression=Set.of(FindingLifecycleRequest.Type.READY_FOR_RETEST,FindingLifecycleRequest.Type.CONFIRM_RESOLVED).contains(r.type());
        if(merge!=(r.otherFindingId()!=null) || merge!=(r.expectedOtherVersion()!=null) || split!=(r.occurrenceId()!=null)
                || regression!=(r.regressionRunId()!=null) || regression!=(r.repairSnapshotRef()!=null)
                || ((merge || split)?r.ownerRef()!=null:r.ownerRef()==null)) throw error(AgentFindingErrorCode.INVALID_REVIEW);
    }
    private ClientException error(AgentFindingErrorCode c) { return new ClientException(c.message(),c); }
}
