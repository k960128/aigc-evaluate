package com.kant.llm.eval.agent.validation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.agent.contract.validation.FixedCaseDefinition;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import com.kant.llm.eval.agent.finding.service.FindingRecordService;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.validation.dao.entity.*;
import com.kant.llm.eval.agent.validation.dao.mapper.*;
import com.kant.llm.eval.agent.validation.dto.*;
import com.kant.llm.eval.agent.validation.support.D04JsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

/** 候选、审核和正式用例版本分开持久化；批准入库绝不会自动发起目标调用。 */
@Service
public class RegressionCaseService {
    private final AgentCaseCandidateMapper candidates;
    private final AgentCaseReviewMapper reviews;
    private final AgentRegressionCaseMapper cases;
    private final FindingRecordService findings;
    private final FixedCaseFactory factory;
    private final ProbePrincipalProvider principals;
    private final D04JsonSupport json;
    public RegressionCaseService(AgentCaseCandidateMapper candidates,AgentCaseReviewMapper reviews,AgentRegressionCaseMapper cases,
            FindingRecordService findings,FixedCaseFactory factory,ProbePrincipalProvider principals,D04JsonSupport json) {
        this.candidates=candidates; this.reviews=reviews; this.cases=cases; this.findings=findings;
        this.factory=factory; this.principals=principals; this.json=json;
    }

    /** 同一发现串行创建候选；幂等重放先于发现版本判断。 */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ValidationResponses.Candidate create(String findingId,ValidationRequests.Candidate request) {
        var p=principals.currentPrincipal(); findings.loadSource(p.tenantId(),p.actorId(),findingId);
        var state=findings.lockCurrent(p.tenantId(),p.actorId(),findingId);
        String hash=json.hash(request);
        var old=candidates.selectOne(new LambdaQueryWrapper<AgentCaseCandidateDO>()
                .eq(AgentCaseCandidateDO::getFindingId,findingId).eq(AgentCaseCandidateDO::getIdempotencyKey,request.idempotencyKey()));
        if(old!=null) { same(hash,old.getRequestHash()); return candidateResponse(old); }
        if(state.getVersion()!=request.expectedFindingVersion().longValue()) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        if(!Set.of("SUSPECTED","INCONCLUSIVE","CONFIRMED").contains(state.getVerificationStatus())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
        if((request.existingCaseId()==null)!=(request.baseCaseVersion()==null)) throw error(AgentFindingErrorCode.INVALID_REVIEW);
        if(request.existingCaseId()!=null) {
            var latest=latest(p.tenantId(),p.actorId(),request.existingCaseId());
            if(!findingId.equals(latest.getFindingId()) || latest.getCaseVersion()!=request.baseCaseVersion().longValue())
                throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        }
        var definition=factory.create(p.tenantId(),p.actorId(),findingId,request.sourceOccurrenceId(),request.preconditions());
        var row=new AgentCaseCandidateDO(); row.setCandidateId(id("candidate_")); row.setFindingId(findingId);
        row.setTenantId(p.tenantId()); row.setActorId(p.actorId()); row.setIdempotencyKey(request.idempotencyKey()); row.setRequestHash(hash);
        row.setDefinitionJson(json.write(definition)); row.setStatus("PENDING_REVIEW"); row.setVersion(0L);
        row.setExistingCaseId(request.existingCaseId()); row.setBaseCaseVersion(request.baseCaseVersion()); row.setCreatedAt(LocalDateTime.now());
        candidates.insert(row); findings.saveState(state); return candidateResponse(row);
    }

    /** 审核锁顺序为发现根→候选；旧正式版本永远只追加、不覆盖。 */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ValidationResponses.CaseReview review(String candidateId,ValidationRequests.CaseReview request) {
        var p=principals.currentPrincipal(); var row=authorizedCandidate(p.tenantId(),p.actorId(),candidateId);
        var state=findings.lockCurrent(p.tenantId(),p.actorId(),row.getFindingId());
        row=authorizedCandidate(p.tenantId(),p.actorId(),candidateId); String hash=json.hash(request);
        var prior=reviews.selectOne(new LambdaQueryWrapper<AgentCaseReviewDO>().eq(AgentCaseReviewDO::getCandidateId,candidateId)
                .eq(AgentCaseReviewDO::getIdempotencyKey,request.idempotencyKey()));
        if(prior!=null) { same(hash,prior.getRequestHash()); return json.read(prior.getResultJson(),ValidationResponses.CaseReview.class); }
        if(row.getVersion()!=request.expectedVersion().longValue() || state.getVersion()!=request.expectedFindingVersion().longValue())
            throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        if(!"PENDING_REVIEW".equals(row.getStatus())) throw error(AgentFindingErrorCode.STATE_CONFLICT);
        String reviewId=id("creview_"); String caseId=null; Long caseVersion=null; LocalDateTime now=LocalDateTime.now();
        if(request.decision()==ValidationRequests.CaseReview.Decision.APPROVE) {
            if(!request.acknowledgeTextOnlyCriterion() || !"CONFIRMED".equals(state.getVerificationStatus())
                    || !p.actorId().equals(state.getOwnerRef()) || Set.of("RESOLVED","ACCEPTED_RISK").contains(state.getRemediationStatus()))
                throw error(AgentFindingErrorCode.STATE_CONFLICT);
            caseId=row.getExistingCaseId(); caseVersion=1L;
            if(caseId==null) caseId=id("case_");
            else {
                var previous=latest(p.tenantId(),p.actorId(),caseId);
                if(!previous.getFindingId().equals(row.getFindingId()) || !previous.getCaseVersion().equals(row.getBaseCaseVersion()))
                    throw error(AgentFindingErrorCode.VERSION_CONFLICT);
                caseVersion=Math.addExact(previous.getCaseVersion(),1);
            }
            var formal=new AgentRegressionCaseDO(); formal.setCaseId(caseId); formal.setCaseVersion(caseVersion);
            formal.setCandidateId(candidateId); formal.setFindingId(row.getFindingId()); formal.setTenantId(p.tenantId()); formal.setActorId(p.actorId());
            formal.setDefinitionJson(row.getDefinitionJson()); formal.setApprovalRef(reviewId); formal.setCreatedAt(now); cases.insert(formal);
            // 固定范围变更必须使旧回归资格失效，不能拿旧范围通过证明新范围修复。
            clearProof(state); state.setRemediationStatus("OPEN");
        }
        row.setStatus(request.decision()==ValidationRequests.CaseReview.Decision.APPROVE?"APPROVED":"RETURNED");
        row.setVersion(Math.addExact(row.getVersion(),1)); candidates.updateById(row); findings.saveState(state);
        var result=new ValidationResponses.CaseReview(reviewId,candidateId,request.decision().name(),row.getVersion(),caseId,caseVersion,
                request.reason(),p.actorId(),json.instant(now));
        var audit=new AgentCaseReviewDO(); audit.setReviewId(reviewId); audit.setCandidateId(candidateId); audit.setIdempotencyKey(request.idempotencyKey());
        audit.setRequestHash(hash); audit.setDecision(request.decision().name()); audit.setReason(request.reason()); audit.setActorId(p.actorId());
        audit.setResultJson(json.write(result)); audit.setCreatedAt(now); reviews.insert(audit); return result;
    }

    public ValidationResponses.Candidate candidate(String id) { var p=principals.currentPrincipal(); return candidateResponse(authorizedCandidate(p.tenantId(),p.actorId(),id)); }
    public List<ValidationResponses.Candidate> candidates(String findingId) {
        var p=principals.currentPrincipal(); findings.loadSource(p.tenantId(),p.actorId(),findingId); findings.requireCanonical(p.tenantId(),p.actorId(),findingId);
        return candidates.selectList(new LambdaQueryWrapper<AgentCaseCandidateDO>().eq(AgentCaseCandidateDO::getFindingId,findingId)
                .eq(AgentCaseCandidateDO::getTenantId,p.tenantId()).eq(AgentCaseCandidateDO::getActorId,p.actorId()).orderByAsc(AgentCaseCandidateDO::getId))
                .stream().map(this::candidateResponse).toList();
    }
    public List<ValidationResponses.CaseReview> reviews(String candidateId) {
        var p=principals.currentPrincipal(); authorizedCandidate(p.tenantId(),p.actorId(),candidateId);
        return reviews.selectList(new LambdaQueryWrapper<AgentCaseReviewDO>().eq(AgentCaseReviewDO::getCandidateId,candidateId).orderByAsc(AgentCaseReviewDO::getId))
                .stream().map(r -> json.read(r.getResultJson(),ValidationResponses.CaseReview.class)).toList();
    }
    public ValidationResponses.FormalCase formal(String id,long version) {
        var p=principals.currentPrincipal(); var c=cases.selectOne(new LambdaQueryWrapper<AgentRegressionCaseDO>().eq(AgentRegressionCaseDO::getCaseId,id)
                .eq(AgentRegressionCaseDO::getCaseVersion,version).eq(AgentRegressionCaseDO::getTenantId,p.tenantId()).eq(AgentRegressionCaseDO::getActorId,p.actorId()));
        if(c==null) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND); return formalResponse(c);
    }
    /** 前端据此选择完整当前回归范围，不需要从字符串猜测最新版本。 */
    public List<ValidationResponses.FormalCase> formalCases(String findingId) {
        var p=principals.currentPrincipal(); findings.loadSource(p.tenantId(),p.actorId(),findingId); findings.requireCanonical(p.tenantId(),p.actorId(),findingId);
        return latestForFinding(p.tenantId(),p.actorId(),findingId).stream().map(this::formalResponse).toList();
    }
    /** 不可变版本历史，用于查看前置条件/断言的审核变更，不覆盖旧版本。 */
    public List<ValidationResponses.FormalCase> versions(String caseId) {
        var p=principals.currentPrincipal(); latest(p.tenantId(),p.actorId(),caseId);
        return cases.selectList(new LambdaQueryWrapper<AgentRegressionCaseDO>().eq(AgentRegressionCaseDO::getCaseId,caseId)
                .eq(AgentRegressionCaseDO::getTenantId,p.tenantId()).eq(AgentRegressionCaseDO::getActorId,p.actorId()).orderByAsc(AgentRegressionCaseDO::getCaseVersion))
                .stream().map(this::formalResponse).toList();
    }
    /** 返回当前范围的全部最新版本；调用者受理回归时冻结此集合。 */
    public List<AgentRegressionCaseDO> latestForFinding(String tenant,String actor,String findingId) {
        Map<String,AgentRegressionCaseDO> versions=new TreeMap<>();
        cases.selectList(new LambdaQueryWrapper<AgentRegressionCaseDO>().eq(AgentRegressionCaseDO::getFindingId,findingId)
                .eq(AgentRegressionCaseDO::getTenantId,tenant).eq(AgentRegressionCaseDO::getActorId,actor).orderByAsc(AgentRegressionCaseDO::getCaseVersion))
                .forEach(c -> versions.put(c.getCaseId(),c)); return List.copyOf(versions.values());
    }
    private AgentRegressionCaseDO latest(String tenant,String actor,String id) {
        var c=cases.selectOne(new LambdaQueryWrapper<AgentRegressionCaseDO>().eq(AgentRegressionCaseDO::getCaseId,id)
                .eq(AgentRegressionCaseDO::getTenantId,tenant).eq(AgentRegressionCaseDO::getActorId,actor)
                .orderByDesc(AgentRegressionCaseDO::getCaseVersion).last("LIMIT 1"));
        if(c==null) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND); return c;
    }
    private AgentCaseCandidateDO authorizedCandidate(String tenant,String actor,String id) {
        var c=candidates.selectOne(new LambdaQueryWrapper<AgentCaseCandidateDO>().eq(AgentCaseCandidateDO::getCandidateId,id)
                .eq(AgentCaseCandidateDO::getTenantId,tenant).eq(AgentCaseCandidateDO::getActorId,actor));
        if(c==null) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND); return c;
    }
    private ValidationResponses.Candidate candidateResponse(AgentCaseCandidateDO c) {
        return new ValidationResponses.Candidate(c.getCandidateId(),c.getFindingId(),c.getStatus(),c.getVersion(),
                json.read(c.getDefinitionJson(),FixedCaseDefinition.class),c.getExistingCaseId(),c.getBaseCaseVersion(),json.instant(c.getCreatedAt()));
    }
    private ValidationResponses.FormalCase formalResponse(AgentRegressionCaseDO c) {
        return new ValidationResponses.FormalCase(c.getCaseId(),c.getCaseVersion(),c.getApprovalRef(),json.read(c.getDefinitionJson(),FixedCaseDefinition.class),json.instant(c.getCreatedAt()));
    }
    public static void clearProof(com.kant.llm.eval.agent.finding.dao.entity.AgentFindingStateDO state) {
        state.setCurrentRegressionRunId(null); state.setRepairSnapshotRef(null); state.setRequiredCasesJson(null); state.setResolvedBy(null); state.setResolutionReason(null);
    }
    private void same(String a,String b) { if(!a.equals(b)) throw error(AgentFindingErrorCode.IDEMPOTENCY_CONFLICT); }
    private String id(String prefix) { return prefix+UUID.randomUUID().toString().replace("-",""); }
    private ClientException error(AgentFindingErrorCode code) { return new ClientException(code.message(),code); }
}
