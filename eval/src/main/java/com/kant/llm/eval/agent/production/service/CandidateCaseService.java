package com.kant.llm.eval.agent.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.agent.port.production.SampleGenerationPort;
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
    private final AgentSampleCandidateMapper candidates;
    private final AgentSampleOperationMapper operations;
    private final SampleRunRecordService records;
    private final ProbePrincipalProvider principals;
    private final FactoryJsonSupport json;

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
        checkPage(afterId, limit);
        if (status != null && !Set.of("PENDING_REVIEW", "QUALITY_REJECTED", "DUPLICATE", "APPROVED", "RETURNED").contains(status)) {
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
        boolean more = rows.size() > limit;
        var page = rows.subList(0, Math.min(rows.size(), limit));
        return new FactoryResponses.Page<>(page.stream().map(this::response).toList(),
                page.isEmpty() ? afterId : page.get(page.size() - 1).getId(), more);
    }

    public FactoryResponses.Candidate detail(String candidateId) {
        var p = principals.currentPrincipal();
        return response(authorized(p.tenantId(), p.actorId(), candidateId));
    }

    /** REFUSED/INVALID可能没有正文，返回null，不伪造一个可以投入评测的攻击输入。 */
    public FactoryResponses.Content content(String candidateId) {
        var p = principals.currentPrincipal();
        var row = authorized(p.tenantId(), p.actorId(), candidateId);
        verifyContent(row);
        return new FactoryResponses.Content(candidateId, row.getContent(), row.getContentHash());
    }

    /** 从根候选到当前候选，最多原始生成+两次改写；父链必须处于同批次同根槽位。 */
    public List<FactoryResponses.Candidate> lineage(String candidateId) {
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
                op.getResultJson() == null ? null : generation(op), json.instant(op.getCreatedAt()))).toList();
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

    public void verifyContent(AgentSampleCandidateDO row) {
        String digest = row.getContent() == null ? null : json.digest(row.getContent());
        if (!Objects.equals(digest, row.getContentHash())) throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
    }

    private FactoryResponses.Candidate response(AgentSampleCandidateDO row) {
        var op = records.operation(row.getOperationId());
        if (!row.getRunId().equals(op.getRunId()) || !"SUCCEEDED".equals(op.getStatus())) {
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        }
        return new FactoryResponses.Candidate(row.getCandidateId(), row.getTaskId(), row.getRunId(), row.getSeedId(),
                row.getOperationId(), row.getOrdinal(), row.getRewriteRound(), row.getParentCandidateId(), row.getContentHash(),
                row.getDuplicateOfCandidateId(), generation(op), json.read(row.getQualityJson(), SampleQualityWorkflow.Report.class),
                row.getStatus(), row.getVersion(), json.instant(row.getCreatedAt()));
    }

    private FactoryResponses.Generation generation(AgentSampleOperationDO op) {
        var result = json.read(op.getResultJson(), SampleGenerationPort.Result.class);
        return new FactoryResponses.Generation(result.status(), result.reason(), result.inputTokens(), result.outputTokens(), result.measurement());
    }

    public static void checkPage(long cursor, int limit) {
        if (cursor < 0 || limit < 1 || limit > 100) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
    }

    private static ClientException error(SampleFactoryErrorCode code) { return new ClientException(code.message(), code); }
}
