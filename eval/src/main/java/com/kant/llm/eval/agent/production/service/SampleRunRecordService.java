package com.kant.llm.eval.agent.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.agent.contract.production.*;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.service.production.SampleQualityWorkflow;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.production.dao.entity.*;
import com.kant.llm.eval.agent.production.dao.mapper.*;
import com.kant.llm.eval.agent.production.dto.*;
import com.kant.llm.eval.agent.production.error.SampleFactoryErrorCode;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/** 单批生产控制面与操作日志。模型调用不在事务内，UNKNOWN保留预留并禁止自动重发。 */
@Service
public class SampleRunRecordService {
    private final AgentSampleRunMapper runs;
    private final AgentSampleOperationMapper operations;
    private final AgentSampleCandidateMapper candidates;
    private final AgentSampleContentIndexMapper index;
    private final SampleProductionService production;
    private final FactoryJsonSupport json;
    private final SampleQualityWorkflow quality = new SampleQualityWorkflow();

    public SampleRunRecordService(AgentSampleRunMapper runs, AgentSampleOperationMapper operations, AgentSampleCandidateMapper candidates,
                                  AgentSampleContentIndexMapper index, SampleProductionService production, FactoryJsonSupport json) {
        this.runs = runs;
        this.operations = operations;
        this.candidates = candidates;
        this.index = index;
        this.production = production;
        this.json = json;
    }

    public AgentSampleRunDO authorized(String tenant, String actor, String id) {
        var r = runs.selectOne(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getRunId, id).eq(AgentSampleRunDO::getTenantId, tenant).eq(AgentSampleRunDO::getActorId, actor));
        if (r == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return r;
    }

    public AgentSampleOperationDO operation(String id) {
        var op = operations.selectOne(new LambdaQueryWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getOperationId, id));
        if (op == null) throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return op;
    }

    public AgentSampleCandidateDO parent(AgentSampleRunDO run) {
        if (run.getParentCandidateId() == null) return null;
        var c = candidates.selectOne(new LambdaQueryWrapper<AgentSampleCandidateDO>().eq(AgentSampleCandidateDO::getCandidateId, run.getParentCandidateId())
                .eq(AgentSampleCandidateDO::getRunId, run.getRunId()).eq(AgentSampleCandidateDO::getOrdinal, run.getOrdinal())
                .eq(AgentSampleCandidateDO::getTenantId, run.getTenantId()).eq(AgentSampleCandidateDO::getActorId, run.getActorId()));
        if (c == null || c.getRewriteRound() + 1 != run.getRewriteRound() || !Objects.equals(c.getContentHash(), c.getContent() == null ? null : json.digest(c.getContent())))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return c;
    }

    public List<AgentSampleRunDO> recoverable() {
        return runs.selectList(new LambdaQueryWrapper<AgentSampleRunDO>().in(AgentSampleRunDO::getStatus, "QUEUED", "RUNNING", "BLOCKED")
                .and(q -> q.isNull(AgentSampleRunDO::getLeaseUntil).or().lt(AgentSampleRunDO::getLeaseUntil, now()))
                .orderByAsc(AgentSampleRunDO::getLeaseUntil).orderByAsc(AgentSampleRunDO::getId).last("LIMIT 20"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String id, String worker) {
        var r = lock(id);
        if (!Set.of("QUEUED", "RUNNING", "BLOCKED").contains(r.getStatus()) || r.getLeaseUntil() != null && r.getLeaseUntil().isAfter(now()))
            return false;
        var update = new LambdaUpdateWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getId, r.getId()).set(AgentSampleRunDO::getWorkerId, worker)
                .set(AgentSampleRunDO::getLeaseUntil, now().plusSeconds(30));
        if ("QUEUED".equals(r.getStatus()))
            update.set(AgentSampleRunDO::getStatus, "RUNNING").setSql("version = version + 1");
        return runs.update(null, update) == 1;
    }

    public boolean heartbeat(String id, String worker) {
        return runs.update(null, new LambdaUpdateWrapper<AgentSampleRunDO>()
                .eq(AgentSampleRunDO::getRunId, id).eq(AgentSampleRunDO::getWorkerId, worker).gt(AgentSampleRunDO::getLeaseUntil, now())
                .in(AgentSampleRunDO::getStatus, "RUNNING", "BLOCKED").set(AgentSampleRunDO::getLeaseUntil, now().plusSeconds(30))) == 1;
    }

    public void release(String id, String worker) {
        var r = runs.selectOne(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getRunId, id).eq(AgentSampleRunDO::getWorkerId, worker));
        if (r == null) return;
        runs.update(null, new LambdaUpdateWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getId, r.getId()).eq(AgentSampleRunDO::getWorkerId, worker)
                .set(AgentSampleRunDO::getWorkerId, null).set(AgentSampleRunDO::getLeaseUntil, "BLOCKED".equals(r.getStatus()) ? now().plusSeconds(30) : null));
    }

    /** 固定操作、调用次数与完整Token预留同事务保存。一个槽位的一次改写只有一个操作ID。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean prepare(String runId, String worker, SampleGenerationPort.Command command, long reservation) {
        var r = owned(runId, worker);
        if (r.getPendingOperationId() != null) return false;
        if (!"RUNNING".equals(r.getStatus())) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        if (Boolean.TRUE.equals(r.getStopRequested()) || !r.getDeadlineAt().isAfter(now())) {
            boolean stopped = Boolean.TRUE.equals(r.getStopRequested());
            terminal(r, stopped ? "STOPPED" : "FAILED", stopped ? "STOP_REQUESTED" : "DEADLINE_EXPIRED");
            save(r);
            return false;
        }
        var task = production.authorizedTask(r.getTenantId(), r.getActorId(), r.getTaskId());
        var spec = production.spec(task);
        var policy = production.budget(task);
        var usage = json.read(r.getUsageJson(), ProductionBudget.Usage.class);
        if (!command.profileRef().equals(spec.generatorProfileRef()) || !command.operationId().equals(operationId(r)))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        if (reservation <= 0 || usage.agentCalls() >= policy.maxAgentCalls() || reservation > policy.maxTokens() - usage.tokens() - usage.reservedTokens()) {
            terminal(r, "FAILED", "PRODUCTION_BUDGET_EXHAUSTED");
            save(r);
            return false;
        }
        var op = new AgentSampleOperationDO();
        op.setOperationId(command.operationId());
        op.setRunId(runId);
        op.setOrdinal(r.getOrdinal());
        op.setRewriteRound(r.getRewriteRound());
        op.setParentCandidateId(r.getParentCandidateId());
        op.setCommandJson(json.write(command));
        op.setReservationTokens(reservation);
        op.setStatus("ACCEPTED");
        op.setCreatedAt(now());
        operations.insert(op);
        r.setPendingOperationId(op.getOperationId());
        r.setUsageJson(json.write(new ProductionBudget.Usage(usage.agentCalls() + 1, usage.tokens(), Math.addExact(usage.reservedTokens(), reservation))));
        save(r);
        return true;
    }

    /** 实际模型调用前的租约/停止/期限围栏。CALLING之后不能因进程重启再次发送。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markCalling(String runId, String worker) {
        var r = owned(runId, worker);
        var op = pending(r);
        if (!"RUNNING".equals(r.getStatus()) || Boolean.TRUE.equals(r.getStopRequested()) || !r.getDeadlineAt().isAfter(now()) || !"ACCEPTED".equals(op.getStatus()))
            return false;
        return operations.update(null, new LambdaUpdateWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getId, op.getId()).eq(AgentSampleOperationDO::getStatus, "ACCEPTED")
                .set(AgentSampleOperationDO::getStatus, "CALLING").set(AgentSampleOperationDO::getDispatchWorkerId, worker)) == 1;
    }

    /** 迟到结果保存到原物理操作事实，不修改批次进度。失租线程也只能追加自己的原操作回执。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResponse(String operationId, String dispatchWorker, SampleGenerationPort.Result result) {
        if (result == null || result.status() == null || !Set.of("GENERATED", "REFUSED", "INVALID").contains(result.status()) || result.inputTokens() < 0 || result.outputTokens() < 0
                || result.measurement() == null || !Set.of("ACTUAL", "ESTIMATED").contains(result.measurement()))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        int changed = operations.update(null, new LambdaUpdateWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getOperationId, operationId)
                .eq(AgentSampleOperationDO::getDispatchWorkerId, dispatchWorker).in(AgentSampleOperationDO::getStatus, "CALLING", "UNKNOWN")
                .set(AgentSampleOperationDO::getResultJson, json.write(result)).set(AgentSampleOperationDO::getStatus, "SUCCEEDED"));
        if (changed != 1) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
    }

    public void recordUnknown(String operationId, String worker) {
        operations.update(null, new LambdaUpdateWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getOperationId, operationId)
                .eq(AgentSampleOperationDO::getDispatchWorkerId, worker).eq(AgentSampleOperationDO::getStatus, "CALLING").set(AgentSampleOperationDO::getStatus, "UNKNOWN"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void block(String runId, String worker) {
        var r = owned(runId, worker);
        if ("BLOCKED".equals(r.getStatus())) return;
        r.setStatus("BLOCKED");
        r.setErrorCode("GENERATION_RESULT_UNKNOWN");
        save(r);
    }

    /** 明确尚未调用时释放预留。已CALLING/UNKNOWN不允许被此入口伪装成零消耗。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelAccepted(String runId, String worker, String errorCode) {
        var r = owned(runId, worker);
        var op = pending(r);
        if (!"ACCEPTED".equals(op.getStatus())) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        var usage = json.read(r.getUsageJson(), ProductionBudget.Usage.class);
        r.setUsageJson(json.write(new ProductionBudget.Usage(usage.agentCalls() - 1, usage.tokens(), usage.reservedTokens() - op.getReservationTokens())));
        op.setStatus("CANCELLED");
        operations.updateById(op);
        r.setPendingOperationId(null);
        terminal(r, Boolean.TRUE.equals(r.getStopRequested()) ? "STOPPED" : "FAILED", errorCode);
        save(r);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void settle(String runId, String worker) {
        var r = owned(runId, worker);
        var op = pending(r);
        if (!"SUCCEEDED".equals(op.getStatus())) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        var task = production.authorizedTask(r.getTenantId(), r.getActorId(), r.getTaskId());
        var spec = production.spec(task);
        var policy = production.budget(task);
        var result = json.read(op.getResultJson(), SampleGenerationPort.Result.class);
        var report = quality.inspect(spec, result);
        var usage = json.read(r.getUsageJson(), ProductionBudget.Usage.class);
        long tokens = Math.addExact(usage.tokens(), Math.addExact(result.inputTokens(), result.outputTokens()));
        var c = new AgentSampleCandidateDO();
        c.setCandidateId("candidate_" + json.digest(op.getOperationId()).substring(0, 32));
        c.setTaskId(r.getTaskId());
        c.setRunId(runId);
        c.setTenantId(r.getTenantId());
        c.setActorId(r.getActorId());
        c.setOperationId(op.getOperationId());
        c.setOrdinal(op.getOrdinal());
        c.setRewriteRound(op.getRewriteRound());
        c.setParentCandidateId(op.getParentCandidateId());
        c.setSeedId(spec.seedId());
        c.setContent(result.content());
        c.setContentHash(result.content() == null ? null : json.digest(result.content()));
        c.setQualityJson(json.write(report));
        c.setStatus("PASS".equals(report.outcome()) ? "PENDING_REVIEW" : "QUALITY_REJECTED");
        c.setVersion(0L);
        c.setCreatedAt(now());
        candidates.insert(c);
        if ("PASS".equals(report.outcome())) deduplicate(c);
        r.setPendingOperationId(null);
        r.setUsageJson(json.write(new ProductionBudget.Usage(usage.agentCalls(), tokens, usage.reservedTokens() - op.getReservationTokens())));
        boolean revise = "REVISE".equals(report.outcome()) && r.getRewriteRound() < spec.maxRewrites() && !Boolean.TRUE.equals(r.getStopRequested())
                && tokens <= policy.maxTokens() && r.getDeadlineAt().isAfter(now());
        if (revise) {
            r.setParentCandidateId(c.getCandidateId());
            r.setRewriteRound(r.getRewriteRound() + 1);
        } else {
            r.setOrdinal(r.getOrdinal() + 1);
            r.setRewriteRound(0);
            r.setParentCandidateId(null);
        }
        if (Boolean.TRUE.equals(r.getStopRequested())) terminal(r, "STOPPED", "STOP_REQUESTED");
        else if (tokens > policy.maxTokens()) terminal(r, "FAILED", "TOKEN_BUDGET_EXCEEDED");
        else if (!r.getDeadlineAt().isAfter(now())) terminal(r, "FAILED", "DEADLINE_EXPIRED");
        else if (r.getOrdinal() >= spec.quantity()) terminal(r, "COMPLETED", null);
        else {
            r.setStatus("RUNNING");
            r.setErrorCode(null);
        }
        save(r);
    }

    /** 跨批次规范精确去重，不冒充语义去重；竞争唯一键失败只查胜者，不重复生成。 */
    private void deduplicate(AgentSampleCandidateDO c) {
        String fingerprint = json.digest(quality.normalized(c.getContent()));
        c.setNormalizedHash(fingerprint);
        var entry = new AgentSampleContentIndexDO();
        entry.setTenantId(c.getTenantId());
        entry.setActorId(c.getActorId());
        entry.setDedupVersion(SampleQualityWorkflow.DEDUP_VERSION);
        entry.setNormalizedHash(fingerprint);
        entry.setCandidateId(c.getCandidateId());
        try {
            index.insert(entry);
        } catch (DuplicateKeyException ex) {
            var first = index.selectOne(new LambdaQueryWrapper<AgentSampleContentIndexDO>().eq(AgentSampleContentIndexDO::getTenantId, c.getTenantId())
                    .eq(AgentSampleContentIndexDO::getActorId, c.getActorId()).eq(AgentSampleContentIndexDO::getDedupVersion, SampleQualityWorkflow.DEDUP_VERSION)
                    .eq(AgentSampleContentIndexDO::getNormalizedHash, fingerprint));
            if (first == null) throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
            c.setDuplicateOfCandidateId(first.getCandidateId());
            c.setStatus("DUPLICATE");
        }
        candidates.updateById(c);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void end(String runId, String worker, String status, String reason) {
        var r = owned(runId, worker);
        if (r.getPendingOperationId() != null || !Set.of("FAILED", "STOPPED").contains(status))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        terminal(r, status, reason);
        save(r);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Control stop(String tenant, String actor, String runId, FactoryRequests.Stop request) {
        authorized(tenant, actor, runId);
        var r = lock(runId);
        String hash = json.hash(request);
        var old = production.controlReplay(runId, request.idempotencyKey(), hash);
        if (old != null) return old;
        if (r.getVersion() != request.expectedVersion().longValue())
            throw error(SampleFactoryErrorCode.VERSION_CONFLICT);
        if (!Set.of("QUEUED", "RUNNING", "BLOCKED").contains(r.getStatus()))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        r.setStopRequested(true);
        if (r.getPendingOperationId() == null) terminal(r, "STOPPED", "STOP_REQUESTED");
        save(r);
        var result = new FactoryResponses.Control(runId, r.getVersion(), null, true, "停止新增生成，保留在途和迟到事实");
        production.saveControl(runId, request.idempotencyKey(), hash, result);
        return result;
    }

    public String operationId(AgentSampleRunDO r) {
        return "genop_" + json.digest(r.getRunId() + ":" + r.getOrdinal() + ":" + r.getRewriteRound()).substring(0, 32);
    }

    /** 恢复/结算前校验固定操作归属当前检查点，不能借其他批次或轮次的回执推进。 */
    public AgentSampleOperationDO pending(AgentSampleRunDO run) {
        if (run.getPendingOperationId() == null) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        var op = operation(run.getPendingOperationId());
        if (!run.getRunId().equals(op.getRunId()) || !run.getOrdinal().equals(op.getOrdinal()) || !run.getRewriteRound().equals(op.getRewriteRound())
                || !Objects.equals(run.getParentCandidateId(), op.getParentCandidateId()) || !operationId(run).equals(op.getOperationId()))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return op;
    }

    private AgentSampleRunDO lock(String id) {
        var r = runs.selectOne(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getRunId, id).last("LIMIT 1 FOR UPDATE"));
        if (r == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return r;
    }

    private AgentSampleRunDO owned(String id, String worker) {
        var r = lock(id);
        if (!Set.of("RUNNING", "BLOCKED").contains(r.getStatus()) || !worker.equals(r.getWorkerId()) || r.getLeaseUntil() == null || !r.getLeaseUntil().isAfter(now()))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        return r;
    }

    private void terminal(AgentSampleRunDO r, String status, String reason) {
        r.setStatus(status);
        r.setErrorCode(reason);
        r.setFinishedAt(now());
    }

    /** 显式清空在途/父候选字段，避免null默认更新策略留下过期引用。 */
    private void save(AgentSampleRunDO r) {
        long previous = r.getVersion();
        r.setVersion(previous + 1);
        int count = runs.update(null, new LambdaUpdateWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getId, r.getId()).eq(AgentSampleRunDO::getVersion, previous)
                .set(AgentSampleRunDO::getVersion, r.getVersion()).set(AgentSampleRunDO::getStatus, r.getStatus()).set(AgentSampleRunDO::getUsageJson, r.getUsageJson())
                .set(AgentSampleRunDO::getOrdinal, r.getOrdinal()).set(AgentSampleRunDO::getRewriteRound, r.getRewriteRound())
                .set(AgentSampleRunDO::getParentCandidateId, r.getParentCandidateId()).set(AgentSampleRunDO::getPendingOperationId, r.getPendingOperationId())
                .set(AgentSampleRunDO::getStopRequested, r.getStopRequested()).set(AgentSampleRunDO::getErrorCode, r.getErrorCode()).set(AgentSampleRunDO::getFinishedAt, r.getFinishedAt()));
        if (count != 1) throw error(SampleFactoryErrorCode.VERSION_CONFLICT);
    }

    private LocalDateTime now() {
        return json.utc(Instant.now());
    }

    private ClientException error(SampleFactoryErrorCode c) {
        return new ClientException(c.message(), c);
    }
}
