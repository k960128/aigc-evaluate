package com.kant.llm.eval.agent.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.port.production.SampleReflectionPort;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.agent.service.production.SampleQualityWorkflow;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleCandidateDO;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleOperationDO;
import com.kant.llm.eval.agent.production.dao.mapper.AgentSampleCandidateMapper;
import com.kant.llm.eval.agent.production.dao.mapper.AgentSampleOperationMapper;
import com.kant.llm.eval.agent.production.dto.FactoryResponses;
import com.kant.llm.eval.agent.production.error.SampleFactoryErrorCode;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 候选工作台的只读业务：按批次筛选质量/审核状态、查看受限原文和改写血缘。
 * 元数据接口不带正文，也不返回实际调用命令或内部思维链。
 */
@Service
public class CandidateCaseService {
    /**
     * 该服务提供前端工作台所需的只读投影，不承担模型调用、质量判断或人工审核。
     * 查询时重新核对候选、生成操作和评审操作的关联，防止“显示成功”掩盖持久化链路不一致。
     */
    /** 候选状态、原文、来源及父链。 */
    private final AgentSampleCandidateMapper candidates;
    /** 生成响应与用量事实；对外只投影元数据，不暴露commandJson。 */
    private final AgentSampleOperationMapper operations;
    /** 批次授权与固定操作读取。 */
    private final SampleRunRecordService records;
    /** 当前登录主体的服务端身份。 */
    private final ProbePrincipalProvider principals;
    /** 持久化JSON读取、原文摘要核对与UTC转换。 */
    private final FactoryJsonSupport json;

    /** 注入只读工作台依赖，不执行质检、改写或审核。 */
    public CandidateCaseService(AgentSampleCandidateMapper candidates, AgentSampleOperationMapper operations,
            SampleRunRecordService records, ProbePrincipalProvider principals, FactoryJsonSupport json) {
        this.candidates = candidates;
        this.operations = operations;
        this.records = records;
        this.principals = principals;
        this.json = json;
    }

    /** 升序主键游标，最多100条；status只能使用候选状态，不与批次或质量状态混用。 */
    public FactoryResponses.Page<FactoryResponses.Candidate> list(String runId, String status, long afterId, int limit) {
        // status过滤的是候选生命周期，不是quality.outcome；两者必须同时展示给前端。
        checkPage(afterId, limit);
        if (status != null && !Set.of("REFLECTING", "PENDING_REVIEW", "QUALITY_REJECTED", "DUPLICATE", "APPROVED", "RETURNED").contains(status)) {
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        }
        var principal = principals.currentPrincipal();
        records.authorized(principal.tenantId(), principal.actorId(), runId);
        var rows = candidates.selectList(new LambdaQueryWrapper<AgentSampleCandidateDO>()
                .eq(AgentSampleCandidateDO::getRunId, runId)
                .eq(AgentSampleCandidateDO::getTenantId, principal.tenantId())
                .eq(AgentSampleCandidateDO::getActorId, principal.actorId())
                .eq(status != null, AgentSampleCandidateDO::getStatus, status)
                .gt(AgentSampleCandidateDO::getId, afterId).orderByAsc(AgentSampleCandidateDO::getId)
                .last("LIMIT " + (limit + 1)));
        // 多读一条只用于判断下一页，nextCursor使用实际返回的最后一条主键。
        // 状态过滤不是冻结快照，审核后刷新待审队列应从游标0开始。
        boolean more = rows.size() > limit;
        var page = rows.subList(0, Math.min(rows.size(), limit));
        return new FactoryResponses.Page<>(page.stream().map(this::response).toList(),
                page.isEmpty() ? afterId : page.get(page.size() - 1).getId(), more);
    }

    /** 返回候选元数据、生成计量及质量报告；原文由content接口单独授权读取。 */
    public FactoryResponses.Candidate detail(String candidateId) {
        // 详情仍只返回索引和质量元数据，正文通过独立接口读取并再次校验摘要。
        var p = principals.currentPrincipal();
        return response(authorized(p.tenantId(), p.actorId(), candidateId));
    }

    /** REFUSED/INVALID可能没有正文，返回null，不伪造一个可以投入评测的攻击输入。 */
    public FactoryResponses.Content content(String candidateId) {
        // 拒绝、非法或空正文候选也可以被审计，但不能被这个接口包装成可执行样本。
        var p = principals.currentPrincipal();
        var row = authorized(p.tenantId(), p.actorId(), candidateId);
        verifyContent(row);
        return new FactoryResponses.Content(candidateId, row.getContent(), row.getContentHash());
    }

    /** 从根候选到当前候选，最多原始生成+两次改写；父链必须处于同批次同根槽位。 */
    public List<FactoryResponses.Candidate> lineage(String candidateId) {
        // 血缘展示用于解释有限改写过程；父候选保留，便于人工比较每一轮变化。
        var p = principals.currentPrincipal();
        var current = authorized(p.tenantId(), p.actorId(), candidateId);
        var chain = new ArrayList<FactoryResponses.Candidate>();
        for (int i = 0; i < 3; i++) {
            chain.add(response(current));
            if (current.getParentCandidateId() == null) {
                Collections.reverse(chain);
                return List.copyOf(chain);
            }
            var parent = authorized(p.tenantId(), p.actorId(), current.getParentCandidateId());
            if (!parent.getRunId().equals(current.getRunId()) || !parent.getOrdinal().equals(current.getOrdinal())
                    || parent.getRewriteRound() + 1 != current.getRewriteRound()) {
                throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
            }
            current = parent;
        }
        throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
    }

    /** 未确认操作通过该索引展示；CALLING/UNKNOWN不提供“自动重发”动作。 */
    public FactoryResponses.Page<FactoryResponses.Operation> operations(String runId, long afterId, int limit) {
        // 操作列表展示的是生命周期和用量，不展示commandJson，避免泄露种子、候选正文和平台提示词。
        checkPage(afterId, limit);
        var p = principals.currentPrincipal();
        records.authorized(p.tenantId(), p.actorId(), runId);
        var rows = operations.selectList(new LambdaQueryWrapper<AgentSampleOperationDO>()
                .eq(AgentSampleOperationDO::getRunId, runId).gt(AgentSampleOperationDO::getId, afterId)
                .orderByAsc(AgentSampleOperationDO::getId).last("LIMIT " + (limit + 1)));
        boolean more = rows.size() > limit;
        var page = rows.subList(0, Math.min(rows.size(), limit));
        var items = page.stream().map(op -> new FactoryResponses.Operation(op.getOperationId(), op.getOrdinal(),
                op.getRewriteRound(), op.getParentCandidateId(), op.getStatus(), op.getReservationTokens(),
                op.getResultJson() == null || !"GENERATE".equals(op.getOperationKind()) ? null : generation(op),
                json.instant(op.getCreatedAt()), op.getOperationKind(), reflection(op))).toList();
        return new FactoryResponses.Page<>(items, page.isEmpty() ? afterId : page.get(page.size() - 1).getId(), more);
    }

    /** 供同一控制面审核业务复用身份隔离，外部接口不能传入tenant/actor。 */
    public AgentSampleCandidateDO authorized(String tenant, String actor, String candidateId) {
        var row = candidates.selectOne(new LambdaQueryWrapper<AgentSampleCandidateDO>()
                .eq(AgentSampleCandidateDO::getCandidateId, candidateId)
                .eq(AgentSampleCandidateDO::getTenantId, tenant).eq(AgentSampleCandidateDO::getActorId, actor));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return row;
    }

    /** 按原始正文核对SHA-256；null正文只允许对应null摘要，不使用去重规范化结果替代原文。 */
    public void verifyContent(AgentSampleCandidateDO row) {
        String digest = row.getContent() == null ? null : json.digest(row.getContent());
        if (!Objects.equals(digest, row.getContentHash())) throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
    }

    /** 组合候选与已确认生成事实，并核对二者属于同一批次；质量状态和人工准入状态分别返回。 */
    private FactoryResponses.Candidate response(AgentSampleCandidateDO row) {
        // 只有生成操作SUCCEEDED后才能形成可读候选；操作成功本身仍不代表质量PASS。
        var op = records.operation(row.getOperationId());
        if (!row.getRunId().equals(op.getRunId()) || !"SUCCEEDED".equals(op.getStatus()) || !"GENERATE".equals(op.getOperationKind())) {
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        }
        return new FactoryResponses.Candidate(row.getCandidateId(), row.getTaskId(), row.getRunId(), row.getSeedId(),
                row.getOperationId(), row.getOrdinal(), row.getRewriteRound(), row.getParentCandidateId(), row.getContentHash(),
                row.getDuplicateOfCandidateId(), generation(op), json.read(row.getQualityJson(), SampleQualityWorkflow.Report.class),
                row.getStatus(), row.getVersion(), json.instant(row.getCreatedAt()), candidateReflection(row));
    }

    /** 只投影生成分类、固定原因和计量，不将响应中的正文混入普通列表。 */
    private FactoryResponses.Generation generation(AgentSampleOperationDO op) {
        var result = json.read(op.getResultJson(), SampleGenerationPort.Result.class);
        return new FactoryResponses.Generation(result.status(), result.reason(), result.inputTokens(), result.outputTokens(), result.measurement());
    }

    /** 候选引用同轮Critic事实；历史纯规则候选和未发起评审的候选返回null，不伪造模型意见。 */
    private FactoryResponses.Reflection candidateReflection(AgentSampleCandidateDO row) {
        String operationId = records.operationId(row.getRunId(), row.getOrdinal(), row.getRewriteRound(), SampleReflectionAgent.Phase.REFLECT);
        var op = operations.selectOne(new LambdaQueryWrapper<AgentSampleOperationDO>()
                .eq(AgentSampleOperationDO::getOperationId, operationId).eq(AgentSampleOperationDO::getRunId, row.getRunId()));
        if (op != null && (!"REFLECT".equals(op.getOperationKind()) || !row.getOrdinal().equals(op.getOrdinal())
                || !row.getRewriteRound().equals(op.getRewriteRound()) || !Objects.equals(row.getParentCandidateId(), op.getParentCandidateId())))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return reflection(op);
    }

    /** 只投影已确认的结构化反思报告及计量，不暴露模型命令或原始响应。 */
    private FactoryResponses.Reflection reflection(AgentSampleOperationDO op) {
        if (op == null || !"REFLECT".equals(op.getOperationKind()) || !"SUCCEEDED".equals(op.getStatus()) || op.getResultJson() == null) return null;
        var result = json.read(op.getResultJson(), SampleReflectionPort.Result.class);
        return new FactoryResponses.Reflection(op.getOperationId(), result.outcome(), result.issues(),
                result.inputTokens(), result.outputTokens(), result.measurement());
    }

    /** 游标必须非负，页大小限定1至100；限制通过后才允许拼接数值LIMIT。 */
    public static void checkPage(long cursor, int limit) {
        if (cursor < 0 || limit < 1 || limit > 100) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
    }

    /** 将读取异常映射为固定业务错误，避免暴露其他主体的数据。 */
    private static ClientException error(SampleFactoryErrorCode code) { return new ClientException(code.message(), code); }
}
