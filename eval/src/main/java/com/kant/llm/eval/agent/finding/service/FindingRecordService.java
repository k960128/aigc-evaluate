package com.kant.llm.eval.agent.finding.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.agent.contract.finding.*;
import com.kant.agent.contract.redteam.*;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.dao.entity.*;
import com.kant.llm.eval.agent.finding.dao.mapper.*;
import com.kant.llm.eval.agent.finding.dto.*;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamFindingDO;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamFindingMapper;
import com.kant.llm.eval.agent.redteam.dto.resp.SuspectedFindingResponse;
import com.kant.llm.eval.agent.redteam.service.AgentRedTeamRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

/**
 * D04发现事实及复核持久化。原始发生表只读，可变确认状态与追加式复核单独保存。
 * 以原始发现行锁串行化首次控制面创建和复核受理，不在事务中调用任何模型。
 */
@Service
public class FindingRecordService {
    private final AgentRedTeamFindingMapper occurrences;
    private final AgentFindingStateMapper states;
    private final AgentFindingReviewMapper reviews;
    private final AgentRedTeamRecordService redTeam;
    private final ObjectMapper json;
    private final AgentFindingOccurrenceMapper memberships;
    private final com.kant.llm.eval.agent.validation.dao.mapper.AgentValidationRunMapper validations;
    private final com.kant.llm.eval.agent.validation.dao.mapper.AgentValidationOccurrenceMapper validationOccurrences;
    private final com.kant.llm.eval.agent.validation.dao.mapper.AgentRegressionCaseMapper cases;
    public FindingRecordService(AgentRedTeamFindingMapper occurrences,AgentFindingStateMapper states,
            AgentFindingReviewMapper reviews,AgentRedTeamRecordService redTeam,ObjectMapper json,
            AgentFindingOccurrenceMapper memberships,com.kant.llm.eval.agent.validation.dao.mapper.AgentValidationRunMapper validations,
            com.kant.llm.eval.agent.validation.dao.mapper.AgentValidationOccurrenceMapper validationOccurrences,
            com.kant.llm.eval.agent.validation.dao.mapper.AgentRegressionCaseMapper cases) {
        this.occurrences=occurrences; this.states=states; this.reviews=reviews; this.redTeam=redTeam; this.json=json;
        this.memberships=memberships; this.validations=validations; this.validationOccurrences=validationOccurrences; this.cases=cases;
    }

    /** 读取原始发生及当时任务配置；归属认证失败统一按不存在处理。 */
    public Source loadSource(String tenant,String actor,String findingId) {
        AgentRedTeamFindingDO occurrence=occurrences.selectOne(new LambdaQueryWrapper<AgentRedTeamFindingDO>()
                .eq(AgentRedTeamFindingDO::getFindingId,findingId).last("LIMIT 1"));
        if (occurrence==null) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND);
        RedTeamRun run=redTeam.findRun(tenant,actor,occurrence.getRunId())
                .orElseThrow(() -> error(AgentFindingErrorCode.FINDING_NOT_FOUND));
        if (!run.taskId().equals(occurrence.getTaskId())) throw error(AgentFindingErrorCode.PERSISTENCE_ERROR);
        RedTeamTask task=redTeam.find(tenant,actor,run.taskId()).orElseThrow(() -> error(AgentFindingErrorCode.FINDING_NOT_FOUND));
        SuspectedFinding finding=new SuspectedFinding(occurrence.getFindingId(),occurrence.getTaskId(),occurrence.getRunId(),
                occurrence.getAttemptId(),occurrence.getActionId(),occurrence.getRiskId(),readRefs(occurrence.getEvidenceRefsJson()),
                occurrence.getReason(),occurrence.getVerificationStatus(),instant(occurrence.getCreatedAt()));
        return new Source(finding,task,redTeam.listActions(tenant,actor,run.runId()));
    }

    /** 查询不会创建控制面；无复核时以SUSPECTED/version=0展示。 */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public FindingDetailResponse detail(String tenant,String actor,String findingId) {
        loadSource(tenant,actor,findingId);
        return detailCanonical(tenant,actor,canonical(tenant,actor,findingId));
    }

    /** 根标识保持不变，供流式映射安全捕获；由外层只读事务调用。 */
    private FindingDetailResponse detailCanonical(String tenant,String actor,String findingId) {
        Source source=loadSource(tenant,actor,findingId);
        AgentFindingStateDO state=findState(tenant,actor,findingId).orElseGet(() -> initial(tenant,actor,findingId));
        List<FindingReviewResponse> latest=reviews.selectList(new LambdaQueryWrapper<AgentFindingReviewDO>()
                .eq(AgentFindingReviewDO::getFindingId,findingId).orderByDesc(AgentFindingReviewDO::getAcceptedVersion).last("LIMIT 100"))
                .stream().map(this::response).sorted(Comparator.comparingLong(FindingReviewResponse::acceptedVersion)).toList();
        List<SuspectedFindingResponse> originalMembers=memberIds(tenant,actor,findingId).stream()
                .map(id -> SuspectedFindingResponse.from(loadSource(tenant,actor,id).finding())).toList();
        var replayRuns=validations.selectList(new LambdaQueryWrapper<com.kant.llm.eval.agent.validation.dao.entity.AgentValidationRunDO>()
                .eq(com.kant.llm.eval.agent.validation.dao.entity.AgentValidationRunDO::getFindingId,findingId)
                .eq(com.kant.llm.eval.agent.validation.dao.entity.AgentValidationRunDO::getKind,"REPLAY"));
        var newOccurrences=validationOccurrences.selectList(new LambdaQueryWrapper<com.kant.llm.eval.agent.validation.dao.entity.AgentValidationOccurrenceDO>()
                .eq(com.kant.llm.eval.agent.validation.dao.entity.AgentValidationOccurrenceDO::getFindingId,findingId));
        List<String> snapshots=new java.util.ArrayList<>(); snapshots.add(source.task().targetSnapshotRef());
        newOccurrences.forEach(o -> { if(!snapshots.contains(o.getTargetSnapshotRef())) snapshots.add(o.getTargetSnapshotRef()); });
        var caseRefs=cases.selectList(new LambdaQueryWrapper<com.kant.llm.eval.agent.validation.dao.entity.AgentRegressionCaseDO>()
                .eq(com.kant.llm.eval.agent.validation.dao.entity.AgentRegressionCaseDO::getFindingId,findingId))
                .stream().map(c -> c.getCaseId()+":"+c.getCaseVersion()).toList();
        return new FindingDetailResponse(findingId,SuspectedFindingResponse.from(source.finding()),
                snapshots,source.task().ruleSetVersion(),null,null,
                state.getVerificationStatus(),state.getRemediationStatus(),state.getSeverity(),state.getOwnerRef(),state.getVersion(),
                replayRuns.stream().map(com.kant.llm.eval.agent.validation.dao.entity.AgentValidationRunDO::getRunId).toList(),latest,
                reviews.selectCount(new LambdaQueryWrapper<AgentFindingReviewDO>().eq(AgentFindingReviewDO::getFindingId,findingId))>latest.size(),
                originalMembers,newOccurrences.stream().map(com.kant.llm.eval.agent.validation.dao.entity.AgentValidationOccurrenceDO::getOccurrenceId).toList(),
                state.getCurrentRegressionRunId(),state.getRepairSnapshotRef(),caseRefs,
                state.getRequiredCasesJson()==null?List.of():readCaseRefs(state.getRequiredCasesJson()),state.getResolvedBy(),state.getResolutionReason());
    }

    /** 游标是上一页末条acceptedVersion，不是原运行动作序号。 */
    public List<FindingReviewResponse> history(String tenant,String actor,String findingId,long afterVersion) {
        if (afterVersion<0) throw error(AgentFindingErrorCode.INVALID_REVIEW);
        loadSource(tenant,actor,findingId);
        return reviews.selectList(new LambdaQueryWrapper<AgentFindingReviewDO>().eq(AgentFindingReviewDO::getFindingId,findingId)
                .gt(AgentFindingReviewDO::getAcceptedVersion,afterVersion)
                .orderByAsc(AgentFindingReviewDO::getAcceptedVersion).last("LIMIT 100")).stream().map(this::response).toList();
    }

    /** 幂等重试优先返回原记录，不再次校验“当前版本仍等于首次期望版本”。 */
    public Optional<FindingReviewResponse> replay(String tenant,String actor,String findingId,String key,String requestHash) {
        loadSource(tenant,actor,findingId);
        return findReview(findingId,key).map(row -> {
            checkSame(row,actor,requestHash); return response(row);
        });
    }

    /** 生成内容摘要，不记录请求正文；包含kind以拒绝自动/人工复核共用同键。 */
    public String requestHash(String kind,Object request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("finding-review-v1:"+kind+":"+write(request)).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256不可用",ex); }
    }

    /**
     * 原子受理复核、增加发现版本并保存证据索引快照。
     * 首版只允许对SUSPECTED/INCONCLUSIVE作确认决策；已确认/已驳回仍可追加不同意见。
     * 更改最终结论、接受风险、确认修复及重新打开需后续独立命令，不旁路回归标准。
     */
    // 先查权限再等待行锁，因此使用READ_COMMITTED，锁后读取必须看到竞争胜者提交的复核。
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public FindingReviewResponse append(String tenant,String actor,String findingId,String key,long expectedVersion,String hash,
            String kind,FindingHumanReviewRequest human,FindingVerificationReport report) {
        // 先做归属检查，再锁同一发生行，避免无权请求长期持有其他租户资源锁。
        loadSource(tenant,actor,findingId);
        AgentRedTeamFindingDO locked=occurrences.selectOne(new LambdaQueryWrapper<AgentRedTeamFindingDO>()
                .eq(AgentRedTeamFindingDO::getFindingId,findingId).last("LIMIT 1 FOR UPDATE"));
        if (locked==null) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND);
        requireCanonical(tenant,actor,findingId);
        Source source=loadSource(tenant,actor,findingId);
        if (!Objects.equals(kind,human==null?"EVIDENCE_CHECK":"HUMAN")) throw error(AgentFindingErrorCode.INVALID_REVIEW);
        Optional<AgentFindingReviewDO> prior=findReview(findingId,key);
        if (prior.isPresent()) { checkSame(prior.get(),actor,hash); return response(prior.get()); }
        AgentFindingStateDO state=findState(tenant,actor,findingId).orElseGet(() -> initial(tenant,actor,findingId));
        if (state.getVersion()!=expectedVersion) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        if (!report.targetSnapshotRef().equals(source.task().targetSnapshotRef())
                || !report.ruleSetVersion().equals(source.task().ruleSetVersion())) throw error(AgentFindingErrorCode.INVALID_REVIEW);
        if (report.integrity()==FindingVerificationReport.Integrity.VALID) {
            List<String> inspected=report.evidenceSnapshot().stream()
                    .flatMap(s -> java.util.stream.Stream.of(s.input().evidenceRef(),s.response().evidenceRef())).toList();
            if (inspected.size()!=source.finding().evidenceRefs().size()
                    || !new HashSet<>(inspected).equals(new HashSet<>(source.finding().evidenceRefs())))
                throw error(AgentFindingErrorCode.EVIDENCE_INSUFFICIENT);
        }
        List<String> cited=human==null?source.finding().evidenceRefs():List.copyOf(human.evidenceRefs());
        if (cited.isEmpty() || new HashSet<>(cited).size()!=cited.size()
                || !source.finding().evidenceRefs().containsAll(cited)) throw error(AgentFindingErrorCode.INVALID_REVIEW);
        String previous=state.getVerificationStatus();
        FindingHumanReviewRequest.Decision decision=human==null?FindingHumanReviewRequest.Decision.COMMENT_ONLY:human.decision();
        if (human!=null) applyDecision(state,human,report,actor);
        LocalDateTime now=LocalDateTime.now();
        if (state.getId()==null) states.insert(state);
        long acceptedVersion=Math.addExact(expectedVersion,1);
        int changed=states.update(null,new LambdaUpdateWrapper<AgentFindingStateDO>()
                .eq(AgentFindingStateDO::getId,state.getId()).eq(AgentFindingStateDO::getVersion,expectedVersion)
                .set(AgentFindingStateDO::getVerificationStatus,state.getVerificationStatus())
                .set(AgentFindingStateDO::getRemediationStatus,state.getRemediationStatus())
                .set(AgentFindingStateDO::getSeverity,state.getSeverity()).set(AgentFindingStateDO::getOwnerRef,state.getOwnerRef())
                .set(AgentFindingStateDO::getVersion,acceptedVersion).set(AgentFindingStateDO::getUpdatedAt,now));
        if (changed!=1) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        AgentFindingReviewDO review=new AgentFindingReviewDO();
        review.setReviewId("review_"+UUID.randomUUID().toString().replace("-","")); review.setFindingId(findingId);
        review.setIdempotencyKey(key); review.setRequestHash(hash); review.setExpectedVersion(expectedVersion);
        review.setAcceptedVersion(acceptedVersion); review.setKind(kind);
        review.setOpinion((human==null?report.opinion():human.opinion()).name()); review.setDecision(decision.name());
        review.setReason(human==null?report.reason():human.reason()); review.setEvidenceRefsJson(write(cited));
        review.setReportJson(write(report)); review.setReviewerRef(actor); review.setPreviousVerificationStatus(previous);
        review.setSeverity(human==null || human.severity()==null?null:human.severity().name());
        review.setOwnerRef(human==null?null:human.ownerRef());
        review.setResultingVerificationStatus(state.getVerificationStatus()); review.setCreatedAt(now);
        reviews.insert(review); return response(review);
    }

    /** 人工确认也不能绕过证据完整性、必填负责人或已有结论的状态边界。 */
    private void applyDecision(AgentFindingStateDO state,FindingHumanReviewRequest request,FindingVerificationReport report,String actor) {
        if (request.decision()==FindingHumanReviewRequest.Decision.COMMENT_ONLY) {
            if (request.severity()!=null || request.ownerRef()!=null) throw error(AgentFindingErrorCode.INVALID_REVIEW);
            return;
        }
        if (!Set.of("SUSPECTED","INCONCLUSIVE").contains(state.getVerificationStatus()))
            throw error(AgentFindingErrorCode.INVALID_REVIEW);
        switch (request.decision()) {
            case CONFIRM -> {
                if (report.integrity()!=FindingVerificationReport.Integrity.VALID) throw error(AgentFindingErrorCode.EVIDENCE_INSUFFICIENT);
                if (request.opinion()!=FindingReviewOpinion.SUPPORT || request.severity()==null || request.severity()==FindingSeverity.PENDING
                        || !actor.equals(request.ownerRef())) throw error(AgentFindingErrorCode.INVALID_REVIEW);
                state.setVerificationStatus(FindingVerificationStatus.CONFIRMED.name());
                state.setRemediationStatus(FindingRemediationStatus.OPEN.name()); state.setSeverity(request.severity().name());
                state.setOwnerRef(request.ownerRef().trim());
            }
            case DISMISS -> {
                if (request.opinion()!=FindingReviewOpinion.OPPOSE || request.severity()!=null || request.ownerRef()!=null)
                    throw error(AgentFindingErrorCode.INVALID_REVIEW);
                state.setVerificationStatus(FindingVerificationStatus.DISMISSED.name());
            }
            case INCONCLUSIVE -> {
                if (!Set.of(FindingReviewOpinion.INSUFFICIENT,FindingReviewOpinion.REPLAY_REQUIRED).contains(request.opinion())
                        || request.severity()!=null || request.ownerRef()!=null) throw error(AgentFindingErrorCode.INVALID_REVIEW);
                state.setVerificationStatus(FindingVerificationStatus.INCONCLUSIVE.name());
            }
            default -> throw error(AgentFindingErrorCode.INVALID_REVIEW);
        }
    }

    private Optional<AgentFindingStateDO> findState(String tenant,String actor,String findingId) {
        return Optional.ofNullable(states.selectOne(new LambdaQueryWrapper<AgentFindingStateDO>()
                .eq(AgentFindingStateDO::getFindingId,findingId).eq(AgentFindingStateDO::getTenantId,tenant)
                .eq(AgentFindingStateDO::getActorId,actor).last("LIMIT 1")));
    }
    private AgentFindingStateDO initial(String tenant,String actor,String findingId) {
        AgentFindingStateDO state=new AgentFindingStateDO(); state.setFindingId(findingId); state.setTenantId(tenant);
        state.setActorId(actor); state.setVerificationStatus(FindingVerificationStatus.SUSPECTED.name());
        state.setSeverity(FindingSeverity.PENDING.name()); state.setVersion(0L);
        state.setCreatedAt(LocalDateTime.now()); state.setUpdatedAt(state.getCreatedAt()); return state;
    }

    /** 读归并根；没有归并记录时原始发生本身就是根。 */
    public String canonical(String tenant,String actor,String id) {
        var row=memberships.selectOne(new LambdaQueryWrapper<AgentFindingOccurrenceDO>().eq(AgentFindingOccurrenceDO::getOccurrenceId,id)
                .eq(AgentFindingOccurrenceDO::getTenantId,tenant).eq(AgentFindingOccurrenceDO::getActorId,actor));
        return row==null?id:row.getFindingId();
    }
    /** 写接口不默默改用另一发现，避免幂等键和expectedVersion被解释到错误资源。 */
    public void requireCanonical(String tenant,String actor,String id) {
        if(!id.equals(canonical(tenant,actor,id))) throw error(AgentFindingErrorCode.STATE_CONFLICT);
    }
    public List<String> memberIds(String tenant,String actor,String root) {
        java.util.Set<String> ids=new java.util.TreeSet<>(); ids.add(root);
        memberships.selectList(new LambdaQueryWrapper<AgentFindingOccurrenceDO>().eq(AgentFindingOccurrenceDO::getFindingId,root)
                .eq(AgentFindingOccurrenceDO::getTenantId,tenant).eq(AgentFindingOccurrenceDO::getActorId,actor)).forEach(m -> ids.add(m.getOccurrenceId()));
        return List.copyOf(ids);
    }
    /** 选择发生时校验归并归属，不能用另一发现的轨迹生成本发现用例。 */
    public Source memberSource(String tenant,String actor,String root,String occurrence) {
        loadSource(tenant,actor,root); requireCanonical(tenant,actor,root);
        if(!memberIds(tenant,actor,root).contains(occurrence)) throw error(AgentFindingErrorCode.FINDING_NOT_FOUND);
        return loadSource(tenant,actor,occurrence);
    }
    /** 所有D04控制面写入共用原始根行锁，调用者必须持有短事务。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public AgentFindingStateDO lockState(String tenant,String actor,String root,long expected) {
        loadSource(tenant,actor,root);
        occurrences.selectOne(new LambdaQueryWrapper<AgentRedTeamFindingDO>().eq(AgentRedTeamFindingDO::getFindingId,root).last("LIMIT 1 FOR UPDATE"));
        requireCanonical(tenant,actor,root);
        AgentFindingStateDO state=findState(tenant,actor,root).orElseGet(() -> initial(tenant,actor,root));
        if(expected>=0 && state.getVersion()!=expected) throw error(AgentFindingErrorCode.VERSION_CONFLICT);
        if(state.getId()==null) states.insert(state);
        return state;
    }
    /** 后台追加验证事实只锁当前版本，不借用客户端已经过期的版本。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public AgentFindingStateDO lockCurrent(String tenant,String actor,String root) {
        return lockState(tenant,actor,root,-1);
    }
    /** 更新版本并显式清空旧回归/修复依据，避免MyBatis忽略null留下旧证明。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void saveState(AgentFindingStateDO state) {
        long next=Math.addExact(state.getVersion(),1);
        int changed=states.update(null,new LambdaUpdateWrapper<AgentFindingStateDO>().eq(AgentFindingStateDO::getId,state.getId())
                .eq(AgentFindingStateDO::getVersion,state.getVersion()).set(AgentFindingStateDO::getVersion,next)
                .set(AgentFindingStateDO::getVerificationStatus,state.getVerificationStatus())
                .set(AgentFindingStateDO::getRemediationStatus,state.getRemediationStatus()).set(AgentFindingStateDO::getOwnerRef,state.getOwnerRef())
                .set(AgentFindingStateDO::getCurrentRegressionRunId,state.getCurrentRegressionRunId())
                .set(AgentFindingStateDO::getRepairSnapshotRef,state.getRepairSnapshotRef()).set(AgentFindingStateDO::getRequiredCasesJson,state.getRequiredCasesJson())
                .set(AgentFindingStateDO::getResolvedBy,state.getResolvedBy()).set(AgentFindingStateDO::getResolutionReason,state.getResolutionReason())
                .set(AgentFindingStateDO::getUpdatedAt,LocalDateTime.now()));
        if(changed!=1) throw error(AgentFindingErrorCode.VERSION_CONFLICT); state.setVersion(next);
    }
    private Optional<AgentFindingReviewDO> findReview(String findingId,String key) {
        return Optional.ofNullable(reviews.selectOne(new LambdaQueryWrapper<AgentFindingReviewDO>()
                .eq(AgentFindingReviewDO::getFindingId,findingId).eq(AgentFindingReviewDO::getIdempotencyKey,key).last("LIMIT 1")));
    }
    private void checkSame(AgentFindingReviewDO row,String actor,String hash) {
        if (!row.getReviewerRef().equals(actor) || !row.getRequestHash().equals(hash)) throw error(AgentFindingErrorCode.IDEMPOTENCY_CONFLICT);
    }
    private FindingReviewResponse response(AgentFindingReviewDO row) {
        try {
            return new FindingReviewResponse(row.getReviewId(),row.getFindingId(),row.getKind(),row.getOpinion(),row.getDecision(),
                    row.getReason(),readRefs(row.getEvidenceRefsJson()),json.readValue(row.getReportJson(),FindingVerificationReport.class),
                    row.getReviewerRef(),row.getSeverity(),row.getOwnerRef(),row.getPreviousVerificationStatus(),row.getResultingVerificationStatus(),
                    row.getAcceptedVersion(),instant(row.getCreatedAt()));
        } catch (JsonProcessingException ex) { throw error(AgentFindingErrorCode.PERSISTENCE_ERROR); }
    }
    private List<String> readRefs(String value) {
        try { return List.copyOf(json.readValue(value,new TypeReference<List<String>>() {})); }
        catch (JsonProcessingException ex) { throw error(AgentFindingErrorCode.PERSISTENCE_ERROR); }
    }
    private List<com.kant.llm.eval.agent.validation.dto.ValidationRequests.CaseRef> readCaseRefs(String value) {
        try { return List.copyOf(json.readValue(value,new TypeReference<List<com.kant.llm.eval.agent.validation.dto.ValidationRequests.CaseRef>>() {})); }
        catch(JsonProcessingException ex) { throw error(AgentFindingErrorCode.PERSISTENCE_ERROR); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw error(AgentFindingErrorCode.PERSISTENCE_ERROR); }
    }
    private Instant instant(LocalDateTime value) { return value.atZone(ZoneId.systemDefault()).toInstant(); }
    private ClientException error(AgentFindingErrorCode code) { return new ClientException(code.message(),code); }

    /** 原始发生、不可变配置与运行轨迹，不含复核后可变结论。 */
    public record Source(SuspectedFinding finding,RedTeamTask task,List<RedTeamActionRecord> actions) {}
}
