package com.kant.llm.eval.agent.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kant.agent.service.production.SampleQualityWorkflow;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.production.dao.entity.*;
import com.kant.llm.eval.agent.production.dao.mapper.*;
import com.kant.llm.eval.agent.production.dto.FactoryRequests;
import com.kant.llm.eval.agent.production.dto.FactoryResponses;
import com.kant.llm.eval.agent.production.error.SampleFactoryErrorCode;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * 人工准入边界：候选审核、正式不可变用例和精确版本用例集。
 * 首版只开放当前登录主体自己的资产；不自动写入没有租户/版本字段的旧dataset表。
 */
@Service
public class AssetReviewService {
    private final AgentSampleCandidateMapper candidateMapper;
    private final AgentSampleReviewMapper reviews;
    private final AgentSampleAssetMapper assets;
    private final AgentSampleCaseSetMapper caseSets;
    private final CandidateCaseService candidates;
    private final SampleProductionService production;
    private final ProbePrincipalProvider principals;
    private final FactoryJsonSupport json;

    public AssetReviewService(AgentSampleCandidateMapper candidateMapper, AgentSampleReviewMapper reviews,
            AgentSampleAssetMapper assets, AgentSampleCaseSetMapper caseSets, CandidateCaseService candidates,
            SampleProductionService production, ProbePrincipalProvider principals, FactoryJsonSupport json) {
        this.candidateMapper = candidateMapper;
        this.reviews = reviews;
        this.assets = assets;
        this.caseSets = caseSets;
        this.candidates = candidates;
        this.production = production;
        this.principals = principals;
        this.json = json;
    }

    /** 审核记录、候选状态和正式资产在同一事务内落库，不能出现“已启用但没有批准记录”。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Review review(String candidateId, FactoryRequests.Review request) {
        var p = principals.currentPrincipal();
        candidates.authorized(p.tenantId(), p.actorId(), candidateId);
        var row = candidateMapper.selectOne(new LambdaQueryWrapper<AgentSampleCandidateDO>()
                .eq(AgentSampleCandidateDO::getCandidateId, candidateId).last("LIMIT 1 FOR UPDATE"));
        String requestHash = json.hash(request);
        var old = reviews.selectOne(new LambdaQueryWrapper<AgentSampleReviewDO>()
                .eq(AgentSampleReviewDO::getCandidateId, candidateId)
                .eq(AgentSampleReviewDO::getIdempotencyKey, request.idempotencyKey()));
        if (old != null) {
            same(requestHash, old.getRequestHash());
            return json.read(old.getResultJson(), FactoryResponses.Review.class);
        }
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        if (row.getVersion() != request.expectedVersion().longValue()) throw error(SampleFactoryErrorCode.VERSION_CONFLICT);
        var quality = json.read(row.getQualityJson(), SampleQualityWorkflow.Report.class);
        if (!"PENDING_REVIEW".equals(row.getStatus()) || row.getDuplicateOfCandidateId() != null
                || !"PASS".equals(quality.outcome()) || !SampleQualityWorkflow.VERSION.equals(quality.version())) {
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        }
        candidates.verifyContent(row);
        var generation = candidates.detail(candidateId).generation();
        if (!"GENERATED".equals(generation.status()) || row.getContent() == null || row.getContent().isBlank()) {
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        }
        boolean approve = request.decision() == FactoryRequests.Review.Decision.APPROVE;
        if (approve && !request.acknowledgeQualityOnly()) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        String reviewId = id("sreview_");
        String caseId = approve ? id("samplecase_") : null;
        Instant now = Instant.now();
        long acceptedVersion = row.getVersion() + 1;
        if (approve) {
            var task = production.authorizedTask(p.tenantId(), p.actorId(), row.getTaskId());
            var spec = production.spec(task);
            // 目录、规则、前置和断言来自专家冻结配置，而不是由模型响应决定。
            var definition = new FactoryResponses.Asset(caseId, 1, "SINGLE_MESSAGE", candidateId, row.getContentHash(),
                    spec.preconditions(), spec.assertionDescription(), spec.riskId(), spec.ruleSetVersion(), null, reviewId, now);
            var asset = new AgentSampleAssetDO();
            asset.setCaseId(caseId); asset.setCaseVersion(1L); asset.setCandidateId(candidateId);
            asset.setTenantId(p.tenantId()); asset.setActorId(p.actorId()); asset.setApprovalRef(reviewId);
            asset.setDefinitionJson(json.write(definition)); asset.setCreatedAt(json.utc(now));
            assets.insert(asset);
        }
        int changed = candidateMapper.update(null, new LambdaUpdateWrapper<AgentSampleCandidateDO>()
                .eq(AgentSampleCandidateDO::getId, row.getId()).eq(AgentSampleCandidateDO::getVersion, row.getVersion())
                .eq(AgentSampleCandidateDO::getStatus, "PENDING_REVIEW")
                .set(AgentSampleCandidateDO::getStatus, approve ? "APPROVED" : "RETURNED")
                .set(AgentSampleCandidateDO::getVersion, acceptedVersion));
        if (changed != 1) throw error(SampleFactoryErrorCode.VERSION_CONFLICT);
        var result = new FactoryResponses.Review(reviewId, candidateId, request.decision().name(), request.reason(),
                p.actorId(), acceptedVersion, caseId, approve ? 1L : null, now);
        var review = new AgentSampleReviewDO();
        review.setReviewId(reviewId); review.setCandidateId(candidateId); review.setIdempotencyKey(request.idempotencyKey());
        review.setRequestHash(requestHash); review.setReason(request.reason()); review.setDecision(request.decision().name());
        review.setResultJson(json.write(result)); review.setCreatedAt(json.utc(now)); reviews.insert(review);
        return result;
    }

    public List<FactoryResponses.Review> reviews(String candidateId) {
        var p = principals.currentPrincipal();
        candidates.authorized(p.tenantId(), p.actorId(), candidateId);
        return reviews.selectList(new LambdaQueryWrapper<AgentSampleReviewDO>()
                .eq(AgentSampleReviewDO::getCandidateId, candidateId).orderByAsc(AgentSampleReviewDO::getId))
                .stream().map(r -> json.read(r.getResultJson(), FactoryResponses.Review.class)).toList();
    }

    /** 精确版本读取，无latest别名；无法看到另一操作者的资产。 */
    public FactoryResponses.Asset asset(String caseId, long version) {
        var p = principals.currentPrincipal();
        return definition(assetRow(p.tenantId(), p.actorId(), caseId, version));
    }

    public FactoryResponses.Content assetContent(String caseId, long version) {
        var p = principals.currentPrincipal();
        var asset = assetRow(p.tenantId(), p.actorId(), caseId, version);
        var definition = definition(asset);
        var candidate = candidates.authorized(p.tenantId(), p.actorId(), asset.getCandidateId());
        candidates.verifyContent(candidate);
        if (!definition.inputRef().equals(candidate.getCandidateId()) || !definition.inputHash().equals(candidate.getContentHash())) {
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        }
        return new FactoryResponses.Content(caseId, candidate.getContent(), candidate.getContentHash());
    }

    public FactoryResponses.Page<FactoryResponses.Asset> assets(long afterId, int limit) {
        CandidateCaseService.checkPage(afterId, limit);
        var p = principals.currentPrincipal();
        var rows = assets.selectList(new LambdaQueryWrapper<AgentSampleAssetDO>()
                .eq(AgentSampleAssetDO::getTenantId, p.tenantId()).eq(AgentSampleAssetDO::getActorId, p.actorId())
                .gt(AgentSampleAssetDO::getId, afterId).orderByAsc(AgentSampleAssetDO::getId).last("LIMIT " + (limit + 1)));
        var page = rows.subList(0, Math.min(rows.size(), limit));
        return new FactoryResponses.Page<>(page.stream().map(this::definition).toList(),
                page.isEmpty() ? afterId : page.get(page.size() - 1).getId(), rows.size() > limit);
    }

    /** 人工选择正式资产组成快照；拒绝候选ID、不存在版本以及重复用例引用。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.CaseSet createCaseSet(FactoryRequests.CaseSet request) {
        var p = principals.currentPrincipal();
        String hash = json.hash(request);
        var old = caseSets.selectOne(new LambdaQueryWrapper<AgentSampleCaseSetDO>()
                .eq(AgentSampleCaseSetDO::getTenantId, p.tenantId()).eq(AgentSampleCaseSetDO::getActorId, p.actorId())
                .eq(AgentSampleCaseSetDO::getIdempotencyKey, request.idempotencyKey()));
        if (old != null) { same(hash, old.getRequestHash()); return caseSetResponse(old); }
        if (new HashSet<>(request.cases()).size() != request.cases().size()) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        for (var ref : request.cases()) assetRow(p.tenantId(), p.actorId(), ref.caseId(), ref.version());
        var row = new AgentSampleCaseSetDO();
        row.setCaseSetId(id("samplecaseset_")); row.setCaseSetVersion(1L); row.setTenantId(p.tenantId()); row.setActorId(p.actorId());
        row.setIdempotencyKey(request.idempotencyKey()); row.setRequestHash(hash); row.setName(request.name());
        row.setCasesJson(json.write(request.cases())); row.setCreatedAt(json.utc(Instant.now()));
        caseSets.insert(row);
        return caseSetResponse(row);
    }

    public FactoryResponses.CaseSet caseSet(String caseSetId, long version) {
        var p = principals.currentPrincipal();
        var row = caseSets.selectOne(new LambdaQueryWrapper<AgentSampleCaseSetDO>()
                .eq(AgentSampleCaseSetDO::getCaseSetId, caseSetId).eq(AgentSampleCaseSetDO::getCaseSetVersion, version)
                .eq(AgentSampleCaseSetDO::getTenantId, p.tenantId()).eq(AgentSampleCaseSetDO::getActorId, p.actorId()));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return caseSetResponse(row);
    }

    public FactoryResponses.Page<FactoryResponses.CaseSet> caseSets(long afterId, int limit) {
        CandidateCaseService.checkPage(afterId, limit);
        var p = principals.currentPrincipal();
        var rows = caseSets.selectList(new LambdaQueryWrapper<AgentSampleCaseSetDO>()
                .eq(AgentSampleCaseSetDO::getTenantId, p.tenantId()).eq(AgentSampleCaseSetDO::getActorId, p.actorId())
                .gt(AgentSampleCaseSetDO::getId, afterId).orderByAsc(AgentSampleCaseSetDO::getId).last("LIMIT " + (limit + 1)));
        var page = rows.subList(0, Math.min(rows.size(), limit));
        return new FactoryResponses.Page<>(page.stream().map(this::caseSetResponse).toList(),
                page.isEmpty() ? afterId : page.get(page.size() - 1).getId(), rows.size() > limit);
    }

    private AgentSampleAssetDO assetRow(String tenant, String actor, String caseId, long version) {
        var row = assets.selectOne(new LambdaQueryWrapper<AgentSampleAssetDO>()
                .eq(AgentSampleAssetDO::getCaseId, caseId).eq(AgentSampleAssetDO::getCaseVersion, version)
                .eq(AgentSampleAssetDO::getTenantId, tenant).eq(AgentSampleAssetDO::getActorId, actor));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return row;
    }

    private FactoryResponses.Asset definition(AgentSampleAssetDO row) {
        var value = json.read(row.getDefinitionJson(), FactoryResponses.Asset.class);
        if (!row.getCaseId().equals(value.caseId()) || row.getCaseVersion() != value.version()
                || !row.getCandidateId().equals(value.inputRef()) || !row.getApprovalRef().equals(value.approvalRef())) {
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        }
        return value;
    }

    private FactoryResponses.CaseSet caseSetResponse(AgentSampleCaseSetDO row) {
        List<FactoryRequests.CaseRef> refs = json.read(row.getCasesJson(), new TypeReference<List<FactoryRequests.CaseRef>>() {});
        return new FactoryResponses.CaseSet(row.getCaseSetId(), row.getCaseSetVersion(), row.getName(), List.copyOf(refs), json.instant(row.getCreatedAt()));
    }

    private void same(String expected, String actual) { if (!expected.equals(actual)) throw error(SampleFactoryErrorCode.IDEMPOTENCY_CONFLICT); }
    private String id(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", ""); }
    private ClientException error(SampleFactoryErrorCode code) { return new ClientException(code.message(), code); }
}
