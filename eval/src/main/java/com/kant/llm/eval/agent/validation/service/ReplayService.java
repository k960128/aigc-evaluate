package com.kant.llm.eval.agent.validation.service;

import com.kant.agent.contract.target.*;
import com.kant.agent.contract.validation.*;
import com.kant.agent.port.target.TargetCapabilityPort;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import com.kant.llm.eval.agent.finding.service.FindingRecordService;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.validation.dto.*;
import com.kant.llm.eval.agent.validation.support.D04JsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** 独立复现与标准评测的固定回归受理。此处不执行目标调用，HTTP只创建持久化计划。 */
@Service
public class ReplayService {
    private final ProbePrincipalProvider principals;
    private final FindingRecordService findings;
    private final FixedCaseFactory factory;
    private final RegressionCaseService cases;
    private final ValidationRunRecordService records;
    private final TargetCapabilityPort targets;
    private final D04JsonSupport json;
    public ReplayService(ProbePrincipalProvider principals,FindingRecordService findings,FixedCaseFactory factory,
            RegressionCaseService cases,ValidationRunRecordService records,TargetCapabilityPort targets,D04JsonSupport json) {
        this.principals=principals; this.findings=findings; this.factory=factory; this.cases=cases; this.records=records; this.targets=targets; this.json=json;
    }

    /** 验证授权目前为外部批准引用；不能把字符串引用声称为已实现完整审批/撤销服务。 */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ValidationResponses.Run create(String findingId,ValidationPlan.Kind kind,ValidationRequests.Run request) {
        var p=principals.currentPrincipal(); findings.loadSource(p.tenantId(),p.actorId(),findingId);
        var state=findings.lockCurrent(p.tenantId(),p.actorId(),findingId); String hash=json.hash(request);
        var prior=records.replay(p.tenantId(),p.actorId(),findingId,kind.name(),request.idempotencyKey(),hash);
        if(prior.isPresent()) return records.response(prior.get());
        if(state.getVersion()!=request.expectedFindingVersion().longValue()) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        if(!request.preconditionsAcknowledged()) throw error(AgentFindingErrorCode.INVALID_REVIEW);
        List<ValidationPlan.CaseBinding> bindings;
        if(kind==ValidationPlan.Kind.REPLAY) {
            if(request.sourceOccurrenceId()==null || request.preconditions()==null || request.cases()!=null && !request.cases().isEmpty())
                throw error(AgentFindingErrorCode.INVALID_REVIEW);
            var definition=factory.create(p.tenantId(),p.actorId(),findingId,request.sourceOccurrenceId(),request.preconditions());
            if(!request.expectedTargetSnapshotRef().equals(definition.baselineSnapshotRef())) throw error(AgentFindingErrorCode.TARGET_UNSUPPORTED);
            bindings=List.of(new ValidationPlan.CaseBinding("replay-source:"+request.sourceOccurrenceId(),1,definition));
        } else {
            if(request.sourceOccurrenceId()!=null || request.preconditions()!=null || request.cases()==null || request.cases().isEmpty()
                    || !"CONFIRMED".equals(state.getVerificationStatus()) || !p.actorId().equals(state.getOwnerRef())
                    || !Set.of("OPEN","IN_PROGRESS","READY_FOR_RETEST","REOPENED").contains(state.getRemediationStatus()))
                throw error(AgentFindingErrorCode.STATE_CONFLICT);
            var latest=cases.latestForFinding(p.tenantId(),p.actorId(),findingId);
            var actual=request.cases().stream().map(c -> c.caseId()+":"+c.version()).sorted().toList();
            var required=latest.stream().map(c -> c.getCaseId()+":"+c.getCaseVersion()).sorted().toList();
            if(required.isEmpty() || !actual.equals(required)) throw error(AgentFindingErrorCode.INVALID_REVIEW);
            bindings=latest.stream().map(c -> new ValidationPlan.CaseBinding(c.getCaseId(),c.getCaseVersion(),
                    json.read(c.getDefinitionJson(),FixedCaseDefinition.class))).toList();
            if(bindings.stream().anyMatch(c -> request.expectedTargetSnapshotRef().equals(c.definition().baselineSnapshotRef())))
                throw error(AgentFindingErrorCode.TARGET_UNSUPPORTED);
        }
        var source=findings.loadSource(p.tenantId(),p.actorId(),findingId);
        var snapshot=targets.getSnapshot(p.tenantId(),p.actorId(),bindings.get(0).definition().target());
        for(var binding:bindings) checkTarget(snapshot,binding.definition(),request.expectedTargetSnapshotRef());
        var plan=new ValidationPlan(kind,source.task().activityId(),"vtask_"+UUID.randomUUID().toString().replace("-",""),
                findingId,snapshot.snapshotRef(),request.authorizationRef(),request.budgetPolicy(),bindings);
        var run=records.create(p.tenantId(),p.actorId(),request.idempotencyKey(),hash,plan);
        if(kind==ValidationPlan.Kind.REGRESSION) {
            RegressionCaseService.clearProof(state); state.setCurrentRegressionRunId(run.getRunId()); state.setRepairSnapshotRef(snapshot.snapshotRef());
            state.setRequiredCasesJson(json.write(bindings.stream().map(c -> new ValidationRequests.CaseRef(c.caseId(),c.version())).toList()));
            state.setRemediationStatus("READY_FOR_RETEST");
        }
        findings.saveState(state); return records.response(run);
    }
    private void checkTarget(TargetSnapshot snapshot,FixedCaseDefinition definition,String expected) {
        if(snapshot.target().targetType()!=TargetType.MODEL || snapshot.status()!=TargetStatus.AVAILABLE
                || !snapshot.snapshotRef().equals(expected) || !snapshot.target().equals(definition.target())
                || !snapshot.environment().equals(definition.environment()) || !snapshot.identityRef().equals(definition.identityRef())
                || !snapshot.capabilities().supports(definition.conversation()?TargetCapability.MULTI_TURN:TargetCapability.SINGLE_TURN))
            throw error(AgentFindingErrorCode.TARGET_UNSUPPORTED);
    }
    public ValidationResponses.Run detail(String runId) { var p=principals.currentPrincipal(); return records.response(records.authorized(p.tenantId(),p.actorId(),runId)); }
    public List<ValidationResponses.Run> history(String findingId) { var p=principals.currentPrincipal(); return records.history(p.tenantId(),p.actorId(),findingId); }
    public List<ValidationResponses.Occurrence> occurrences(String findingId) { var p=principals.currentPrincipal(); return records.occurrences(p.tenantId(),p.actorId(),findingId); }
    public List<com.kant.agent.contract.assessment.Assessment> assessments(String runId) { var p=principals.currentPrincipal(); return records.assessments(p.tenantId(),p.actorId(),runId); }
    public ValidationResponses.Stop stop(String runId,ValidationRequests.Stop request) { var p=principals.currentPrincipal(); return records.stop(p.tenantId(),p.actorId(),runId,request); }
    private ClientException error(AgentFindingErrorCode c) { return new ClientException(c.message(),c); }
}
