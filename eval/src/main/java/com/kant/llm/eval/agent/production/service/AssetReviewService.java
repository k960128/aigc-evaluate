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
 * <p>审核是独立请求，不恢复或改变生成批次的终态；FAILED批次中的合格候选仍可审核。
 * APPROVE只确认样本准入，不证明目标存在风险；RETURN只记退回，不自动唤醒生成Agent改写。</p>
 */
@Service
public class AssetReviewService {
    /** 候选行锁及版本化准入状态更新。 */
    private final AgentSampleCandidateMapper candidateMapper;
    /** 人工意见、审批依据及首次审核回执。 */
    private final AgentSampleReviewMapper reviews;
    /** 已批准的不可变正式单消息用例。 */
    private final AgentSampleAssetMapper assets;
    /** 人工选择的正式用例精确版本集合。 */
    private final AgentSampleCaseSetMapper caseSets;
    /** 复用候选归属、生成事实及正文完整性检查。 */
    private final CandidateCaseService candidates;
    /** 读取专家冻结的规则、前置条件及断言说明。 */
    private final SampleProductionService production;
    /** 审核人来自服务端认证，不能由请求参数覆盖。 */
    private final ProbePrincipalProvider principals;
    /** JSON回执与定义转换、时间精度统一。 */
    private final FactoryJsonSupport json;

    /** 注入准入和查询依赖；该服务不持有生成模型或目标执行端口。 */
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

    /**
     * 人工批准或退回候选，审核记录、候选状态和正式资产在同一事务内落库。
     * @param candidateId 质量PASS、未重复且处于PENDING_REVIEW的当前主体候选
     * @param request 候选版本、幂等键、人工决定及理由；批准时须确认质检不等于风险验证
     * @return 首次审核回执，只有APPROVE包含正式caseId与版本
     */
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
        // 幂等检查在版本检查之前，重复发送原审核请求不因首次操作已递增版本而报错。
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        if (row.getVersion() != request.expectedVersion().longValue()) throw error(SampleFactoryErrorCode.VERSION_CONFLICT);
        var quality = json.read(row.getQualityJson(), SampleQualityWorkflow.Report.class);
        if (!"PENDING_REVIEW".equals(row.getStatus()) || row.getDuplicateOfCandidateId() != null
                || !"PASS".equals(quality.outcome()) || !SampleQualityWorkflow.VERSION.equals(quality.version())) {
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        }
        // 准入时重新核对质量版本、生成事实与原文摘要，不能只相信前端展示的PASS标签。
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
        // 资产插入与候选状态更新同事务；下面任一步失败，前面创建的资产也会回滚。
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

    /** 校验候选归属后按创建顺序返回审核回执，不重新计算或覆盖历史人工意见。 */
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

    /** 通过正式版本读取来源候选正文，同时核对资产引用、冻结哈希和当前存储正文的一致性。 */
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

    /** 按主键游标分页读取已批准资产，多取一条判断hasMore，不返回正文。 */
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

    /**
     * 人工选择正式资产组成有序不可变快照；拒绝候选ID、不存在版本及重复引用。
     * 同键同请求复用原集合，不自动开始评测，也不转换成旧dataset表记录。
     */
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

    /** 按当前主体与精确集合版本查询，不支持latest，也不会替换集合中的旧用例版本。 */
    public FactoryResponses.CaseSet caseSet(String caseSetId, long version) {
        var p = principals.currentPrincipal();
        var row = caseSets.selectOne(new LambdaQueryWrapper<AgentSampleCaseSetDO>()
                .eq(AgentSampleCaseSetDO::getCaseSetId, caseSetId).eq(AgentSampleCaseSetDO::getCaseSetVersion, version)
                .eq(AgentSampleCaseSetDO::getTenantId, p.tenantId()).eq(AgentSampleCaseSetDO::getActorId, p.actorId()));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return caseSetResponse(row);
    }

    /** 返回当前主体的集合游标页，集合内保留原选择顺序。 */
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

    /** 按归属与精确版本定位正式资产；不存在和越权统一返回不可见。 */
    private AgentSampleAssetDO assetRow(String tenant, String actor, String caseId, long version) {
        var row = assets.selectOne(new LambdaQueryWrapper<AgentSampleAssetDO>()
                .eq(AgentSampleAssetDO::getCaseId, caseId).eq(AgentSampleAssetDO::getCaseVersion, version)
                .eq(AgentSampleAssetDO::getTenantId, tenant).eq(AgentSampleAssetDO::getActorId, actor));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return row;
    }

    /** 解码正式定义并核对主字段与JSON中的身份、来源、审批引用，防止读取不一致的持久化事实。 */
    private FactoryResponses.Asset definition(AgentSampleAssetDO row) {
        var value = json.read(row.getDefinitionJson(), FactoryResponses.Asset.class);
        if (!row.getCaseId().equals(value.caseId()) || row.getCaseVersion() != value.version()
                || !row.getCandidateId().equals(value.inputRef()) || !row.getApprovalRef().equals(value.approvalRef())) {
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        }
        return value;
    }

    /** 使用泛型类型信息恢复有序正式引用，并复制为不可变集合返回。 */
    private FactoryResponses.CaseSet caseSetResponse(AgentSampleCaseSetDO row) {
        List<FactoryRequests.CaseRef> refs = json.read(row.getCasesJson(), new TypeReference<List<FactoryRequests.CaseRef>>() {});
        return new FactoryResponses.CaseSet(row.getCaseSetId(), row.getCaseSetVersion(), row.getName(), List.copyOf(refs), json.instant(row.getCreatedAt()));
    }

    /** 拒绝相同幂等键携带不同审核或集合请求。 */
    private void same(String expected, String actual) { if (!expected.equals(actual)) throw error(SampleFactoryErrorCode.IDEMPOTENCY_CONFLICT); }
    /** 创建带业务前缀的审批、用例或集合ID，不作为分页游标。 */
    private String id(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", ""); }
    /** 构造固定D05错误消息，不泄露被拒绝访问的资产内容。 */
    private ClientException error(SampleFactoryErrorCode code) { return new ClientException(code.message(), code); }
}
