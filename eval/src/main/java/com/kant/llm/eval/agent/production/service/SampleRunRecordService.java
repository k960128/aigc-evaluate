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

/**
 * D05单批生产状态机及调用账本：维护租约、操作事实、Token预留、候选与执行检查点。
 * <p>一次生成依次经过prepare预留、markCalling发送围栏、recordResponse记录响应和settle结算。
 * 这些步骤各自使用短事务，实际模型请求在事务外执行，避免持锁等待远端响应。</p>
 * <p>操作SUCCEEDED只表示响应已确认，不表示质量通过。CALLING/UNKNOWN结果未确认时保留预留，
 * 不以重启、超期或停止为由自动重发或归零用量；只有有效租约的Worker可以推进批次。</p>
 */
@Service
public class SampleRunRecordService {
    /** 批次状态、检查点与租约。 */
    private final AgentSampleRunMapper runs;
    /** 一次物理生成请求的固定命令、发送状态和响应事实。 */
    private final AgentSampleOperationMapper operations;
    /** 原生成及每次改写各自形成的候选，父正文永不覆盖。 */
    private final AgentSampleCandidateMapper candidates;
    /** 主体内跨批次的规范化精确去重索引。 */
    private final AgentSampleContentIndexMapper index;
    /** 读取冻结计划及复用控制命令幂等回执。 */
    private final SampleProductionService production;
    /** JSON、摘要及时间转换工具。 */
    private final FactoryJsonSupport json;
    /** 无模型调用的确定性质量规则。 */
    private final SampleQualityWorkflow quality = new SampleQualityWorkflow();

    /** 注入账本与计划依赖，不在构造时启动Worker或生成操作。 */
    public SampleRunRecordService(AgentSampleRunMapper runs, AgentSampleOperationMapper operations, AgentSampleCandidateMapper candidates,
                                  AgentSampleContentIndexMapper index, SampleProductionService production, FactoryJsonSupport json) {
        this.runs = runs;
        this.operations = operations;
        this.candidates = candidates;
        this.index = index;
        this.production = production;
        this.json = json;
    }

    /** 按服务端主体读取批次；外部入口不能自行传入他人的tenant/actor。 */
    public AgentSampleRunDO authorized(String tenant, String actor, String id) {
        var r = runs.selectOne(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getRunId, id).eq(AgentSampleRunDO::getTenantId, tenant).eq(AgentSampleRunDO::getActorId, actor));
        if (r == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return r;
    }

    /** 内部按固定操作ID读事实；调用方须先检查批次/候选归属，本方法本身不做登录校验。 */
    public AgentSampleOperationDO operation(String id) {
        var op = operations.selectOne(new LambdaQueryWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getOperationId, id));
        if (op == null) throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return op;
    }

    /** 读取当前改写的上一轮候选并校验同主体、同批次、同槽位、连续轮次及正文哈希；首轮返回null。 */
    public AgentSampleCandidateDO parent(AgentSampleRunDO run) {
        if (run.getParentCandidateId() == null) return null;
        var c = candidates.selectOne(new LambdaQueryWrapper<AgentSampleCandidateDO>().eq(AgentSampleCandidateDO::getCandidateId, run.getParentCandidateId())
                .eq(AgentSampleCandidateDO::getRunId, run.getRunId()).eq(AgentSampleCandidateDO::getOrdinal, run.getOrdinal())
                .eq(AgentSampleCandidateDO::getTenantId, run.getTenantId()).eq(AgentSampleCandidateDO::getActorId, run.getActorId()));
        if (c == null || c.getRewriteRound() + 1 != run.getRewriteRound() || !Objects.equals(c.getContentHash(), c.getContent() == null ? null : json.digest(c.getContent())))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return c;
    }

    /** 最多扫描20个无有效租约的非终态批次；被选中不代表允许重发原模型请求。 */
    public List<AgentSampleRunDO> recoverable() {
        return runs.selectList(new LambdaQueryWrapper<AgentSampleRunDO>().in(AgentSampleRunDO::getStatus, "QUEUED", "RUNNING", "BLOCKED")
                .and(q -> q.isNull(AgentSampleRunDO::getLeaseUntil).or().lt(AgentSampleRunDO::getLeaseUntil, now()))
                .orderByAsc(AgentSampleRunDO::getLeaseUntil).orderByAsc(AgentSampleRunDO::getId).last("LIMIT 20"));
    }

    /** 行锁内领取30秒租约；排队批次转RUNNING并递增版本，已被其他Worker占有则返回false。 */
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

    /** 仅为仍持有有效租约的Worker续租30秒；失租或批次已结束时返回false，不能复活执行权。 */
    public boolean heartbeat(String id, String worker) {
        return runs.update(null, new LambdaUpdateWrapper<AgentSampleRunDO>()
                .eq(AgentSampleRunDO::getRunId, id).eq(AgentSampleRunDO::getWorkerId, worker).gt(AgentSampleRunDO::getLeaseUntil, now())
                .in(AgentSampleRunDO::getStatus, "RUNNING", "BLOCKED").set(AgentSampleRunDO::getLeaseUntil, now().plusSeconds(30))) == 1;
    }

    /** 按Worker身份释放执行权；BLOCKED保留30秒冷却，避免后台反复立即领取未知批次。 */
    public void release(String id, String worker) {
        var r = runs.selectOne(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getRunId, id).eq(AgentSampleRunDO::getWorkerId, worker));
        if (r == null) return;
        runs.update(null, new LambdaUpdateWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getId, r.getId()).eq(AgentSampleRunDO::getWorkerId, worker)
                .set(AgentSampleRunDO::getWorkerId, null).set(AgentSampleRunDO::getLeaseUntil, "BLOCKED".equals(r.getStatus()) ? now().plusSeconds(30) : null));
    }

    /**
     * 固定操作、调用次数与完整Token预留同事务保存，一个槽位的一次改写只有一个操作ID。
     * @param runId 当前生产批次
     * @param worker 必须仍持有租约的执行者
     * @param command 已绑定生成角色、系统提示词及本轮输入的固定命令
     * @param reservation 本次输入估算加最大输出的预留Token，尚不是实际消耗
     * @return 是否成功创建ACCEPTED操作；停止、超期或预算不足时不会发出请求
     */
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
        // 有余额也可能不足以覆盖下一次预留；先扣除实际消耗和所有尚未释放的预留再比较。
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

    /** 原发送者未能确认响应时将CALLING记为UNKNOWN；不删除固定命令、不释放预算、不补发请求。 */
    public void recordUnknown(String operationId, String worker) {
        operations.update(null, new LambdaUpdateWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getOperationId, operationId)
                .eq(AgentSampleOperationDO::getDispatchWorkerId, worker).eq(AgentSampleOperationDO::getStatus, "CALLING").set(AgentSampleOperationDO::getStatus, "UNKNOWN"));
    }

    /** 暂停批次推进并保留在途操作；BLOCKED不是终态，迟到的已确认响应仍可在以后结算。 */
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

    /**
     * 结算已确认响应：候选、质量报告、去重索引、Token账本与槽位检查点在同一事务内提交。
     * <p>即使响应为REFUSED/INVALID也保存候选事实并计入已发生的用量。
     * 待人工审核只是候选状态，不暂停批次，Worker可继续处理后续槽位。</p>
     * @param runId 待结算批次，其pendingOperationId必须指向当前槽位的SUCCEEDED操作
     * @param worker 当前有效租约持有者，不一定是原请求发送者
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void settle(String runId, String worker) {
        var r = owned(runId, worker);
        var op = pending(r);
        if (!"SUCCEEDED".equals(op.getStatus())) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        var task = production.authorizedTask(r.getTenantId(), r.getActorId(), r.getTaskId());
        var spec = production.spec(task);
        var policy = production.budget(task);
        var result = json.read(op.getResultJson(), SampleGenerationPort.Result.class);
        // 质检是本地规则，不是模型安全判定；原响应已确认和质检通过属于不同维度。
        var report = quality.inspect(spec, result);
        var usage = json.read(r.getUsageJson(), ProductionBudget.Usage.class);
        long tokens = Math.addExact(usage.tokens(), Math.addExact(result.inputTokens(), result.outputTokens()));
        var c = new AgentSampleCandidateDO();
        // 由操作身份导出候选身份，配合事务和唯一键避免恢复时为同一响应生成多个候选。
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
        // 只有REVISE允许有限改写；拒绝、重复或低质量不会无限补足“通过数量”。
        // 下一轮仍需经过prepare预留检查，这里仅判断是否还有改写资格。
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
        // 停止、实际超额和超期优先于完成；COMPLETED只表示根槽位全部结束，不代表全部合格或已审核。
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
            // 同主体同版本的规范化正文只能占一个索引；首候选即使后来退回，也不会自动释放索引。
            var first = index.selectOne(new LambdaQueryWrapper<AgentSampleContentIndexDO>().eq(AgentSampleContentIndexDO::getTenantId, c.getTenantId())
                    .eq(AgentSampleContentIndexDO::getActorId, c.getActorId()).eq(AgentSampleContentIndexDO::getDedupVersion, SampleQualityWorkflow.DEDUP_VERSION)
                    .eq(AgentSampleContentIndexDO::getNormalizedHash, fingerprint));
            if (first == null) throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
            c.setDuplicateOfCandidateId(first.getCandidateId());
            c.setStatus("DUPLICATE");
        }
        candidates.updateById(c);
    }

    /** 仅在没有待结算操作时明确结束为FAILED或STOPPED，不能用终态掩盖未知调用账目。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void end(String runId, String worker, String status, String reason) {
        var r = owned(runId, worker);
        if (r.getPendingOperationId() != null || !Set.of("FAILED", "STOPPED").contains(status))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        terminal(r, status, reason);
        save(r);
    }

    /**
     * 人工请求停止新增生成，expectedVersion来自批次；状态变更与命令回执同事务保存。
     * 有在途操作时仅记录意图，不能承诺取消供应商请求；无待结算操作时立即转STOPPED。
     */
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

    /** 由批次、0基根槽位、改写轮次确定操作身份；恢复同一检查点不会换成新的调用身份。 */
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

    /** 事务内锁定单批次；本方法不负责主体隔离，外部入口必须先完成授权校验。 */
    private AgentSampleRunDO lock(String id) {
        var r = runs.selectOne(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getRunId, id).last("LIMIT 1 FOR UPDATE"));
        if (r == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return r;
    }

    /** 加锁后验证非终态、Worker身份及租约有效期，阻止过期线程推进状态。 */
    private AgentSampleRunDO owned(String id, String worker) {
        var r = lock(id);
        if (!Set.of("RUNNING", "BLOCKED").contains(r.getStatus()) || !worker.equals(r.getWorkerId()) || r.getLeaseUntil() == null || !r.getLeaseUntil().isAfter(now()))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        return r;
    }

    /** 仅修改内存对象的终态、原因和结束时间；由调用方save持久化，租约由Worker最终释放。 */
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

    /** 返回匹配数据库DATETIME(6)精度的UTC时间。 */
    private LocalDateTime now() {
        return json.utc(Instant.now());
    }

    /** 使用固定错误码收口，不在异常消息中附加候选、命令或供应商原文。 */
    private ClientException error(SampleFactoryErrorCode c) {
        return new ClientException(c.message(), c);
    }
}
