package com.kant.llm.eval.agent.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.agent.contract.production.*;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.port.production.SampleReflectionPort;
import com.kant.agent.service.production.SampleGenerationWorkflow;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.agent.service.production.SampleQualityWorkflow;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.production.config.SampleFactoryModelConfiguration;
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
 * D05 Reflection持久化运行时：维护租约、生成/评审操作事实、Token预留、候选与阶段检查点。
 * <p>一次生成依次经过prepare预留、markCalling发送围栏、recordResponse记录响应和settle结算。
 * 这些步骤各自使用短事务，实际模型请求在事务外执行，避免持锁等待远端响应。</p>
 * <p>操作SUCCEEDED只表示响应已确认，不表示质量通过。CALLING/UNKNOWN结果未确认时保留预留，
 * 不以重启、超期或停止为由自动重发或归零用量；只有有效租约的Worker可以推进批次。</p>
 */
@Service
public class SampleRunRecordService {
    /**
     * 该服务是D05的持久化状态机，而不是普通CRUD服务。
     * 每个公开方法都围绕一个检查点推进：先锁定批次，再校验租约、版本和操作身份，最后原子保存状态。
     */
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
    /** 唯一的生成/评审/改写决策主体，本服务只把决策和账目原子落库。 */
    private final SampleReflectionAgent agent;
    /** 在事务边界验证计划组合身份与单次角色身份，不让不同模型的检查点混用。 */
    private final SampleFactoryModelConfiguration models;

    /** 注入账本与计划依赖，不在构造时启动Worker或生成操作。 */
    public SampleRunRecordService(AgentSampleRunMapper runs, AgentSampleOperationMapper operations, AgentSampleCandidateMapper candidates,
                                  AgentSampleContentIndexMapper index, SampleProductionService production, FactoryJsonSupport json,
                                  SampleReflectionAgent agent, SampleFactoryModelConfiguration models) {
        this.runs = runs;
        this.operations = operations;
        this.candidates = candidates;
        this.index = index;
        this.production = production;
        this.json = json;
        this.agent = agent;
        this.models = models;
    }

    /** 按服务端主体读取批次；外部入口不能自行传入他人的tenant/actor。 */
    public AgentSampleRunDO authorized(String tenant, String actor, String id) {
        // HTTP查询和人工审核使用主体隔离；Worker内部则由领取时保存的tenant/actor继续校验。
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
        // QUEUED、租约过期RUNNING和BLOCKED都可以被扫描；是否允许继续推进仍由claim/advance再次判断。
        return runs.selectList(new LambdaQueryWrapper<AgentSampleRunDO>().in(AgentSampleRunDO::getStatus, "QUEUED", "RUNNING", "BLOCKED")
                .and(q -> q.isNull(AgentSampleRunDO::getLeaseUntil).or().lt(AgentSampleRunDO::getLeaseUntil, now()))
                .orderByAsc(AgentSampleRunDO::getLeaseUntil).orderByAsc(AgentSampleRunDO::getId).last("LIMIT 20"));
    }

    /** 行锁内领取30秒租约；排队批次转RUNNING并递增版本，已被其他Worker占有则返回false。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String id, String worker) {
        // claim是跨线程、跨实例的执行权竞争；内存active集合只能辅助心跳，不能代替这里的数据库条件更新。
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
        // 心跳只延长当前有效租约，不能把已失租或已终态的批次重新变成可执行。
        return runs.update(null, new LambdaUpdateWrapper<AgentSampleRunDO>()
                .eq(AgentSampleRunDO::getRunId, id).eq(AgentSampleRunDO::getWorkerId, worker).gt(AgentSampleRunDO::getLeaseUntil, now())
                .in(AgentSampleRunDO::getStatus, "RUNNING", "BLOCKED").set(AgentSampleRunDO::getLeaseUntil, now().plusSeconds(30))) == 1;
    }

    /** 按Worker身份释放执行权；BLOCKED保留30秒冷却，避免后台反复立即领取未知批次。 */
    public void release(String id, String worker) {
        // 释放执行权后，批次仍由数据库状态决定是否可恢复；释放不删除操作和候选事实。
        var r = runs.selectOne(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getRunId, id).eq(AgentSampleRunDO::getWorkerId, worker));
        if (r == null) return;
        runs.update(null, new LambdaUpdateWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getId, r.getId()).eq(AgentSampleRunDO::getWorkerId, worker)
                .set(AgentSampleRunDO::getWorkerId, null).set(AgentSampleRunDO::getLeaseUntil, "BLOCKED".equals(r.getStatus()) ? now().plusSeconds(30) : null));
    }

    /**
     * 固定操作、调用次数与完整Token预留同事务保存；同轮的生成与评审各自拥有唯一操作ID。
     * @param runId 当前生产批次
     * @param worker 必须仍持有租约的执行者
     * @param command 已绑定当前生成或评审角色、系统提示词及本轮输入的固定命令
     * @param reservation 本次输入估算加最大输出的预留Token，尚不是实际消耗
     * @return 是否成功创建ACCEPTED操作；停止、超期或预算不足时不会发出请求
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean prepare(String runId, String worker, SampleGenerationPort.Command command, long reservation) {
        // prepare只产生ACCEPTED操作并预留额度；只有后续markCalling成功才允许进入实际网络调用。
        var r = owned(runId, worker);
        if (r.getPendingOperationId() != null) return false;
        if (!"RUNNING".equals(r.getStatus())) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        if (!SampleReflectionAgent.VERSION.equals(r.getWorkflowVersion())) {
            terminal(r, "FAILED", "REFLECTION_UPGRADE_REQUIRED");
            save(r);
            return false;
        }
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
        if (!models.matches(spec.generatorProfileRef())) {
            terminal(r, "FAILED", "GENERATOR_CONFIGURATION_CHANGED");
            save(r);
            return false;
        }
        if (!command.profileRef().equals(models.role(phase(r)).profile().profileRef()) || !command.operationId().equals(operationId(r)))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        String expectedPrompt = phase(r) == SampleReflectionAgent.Phase.REFLECT
                ? SampleReflectionAgent.CRITIC_PROMPT : SampleGenerationWorkflow.SYSTEM_PROMPT;
        if (!expectedPrompt.equals(command.systemPrompt())) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
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
        op.setOperationKind(phase(r).name());
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

    /** 实际调用前再次检查租约、停止、期限及双角色配置；CALLING之后不能因重启再次发送。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markCalling(String runId, String worker) {
        // markCalling是“即将发送”的最后持久化围栏；进入CALLING后不能由恢复逻辑自动再次发送同一操作。
        var r = owned(runId, worker);
        var op = pending(r);
        if (!"RUNNING".equals(r.getStatus()) || Boolean.TRUE.equals(r.getStopRequested()) || !r.getDeadlineAt().isAfter(now()) || !"ACCEPTED".equals(op.getStatus()))
            return false;
        var task = production.authorizedTask(r.getTenantId(), r.getActorId(), r.getTaskId());
        var command = json.read(op.getCommandJson(), SampleGenerationPort.Command.class);
        if (!models.matches(production.spec(task).generatorProfileRef())
                || !models.role(phase(r)).profile().profileRef().equals(command.profileRef())) return false;
        return operations.update(null, new LambdaUpdateWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getId, op.getId()).eq(AgentSampleOperationDO::getStatus, "ACCEPTED")
                .set(AgentSampleOperationDO::getStatus, "CALLING").set(AgentSampleOperationDO::getDispatchWorkerId, worker)) == 1;
    }

    /** 迟到结果保存到原物理操作事实，不修改批次进度。失租线程也只能追加自己的原操作回执。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResponse(String operationId, String dispatchWorker, SampleGenerationPort.Result result) {
        // 生成响应确认只更新operation，不直接推进run；推进由settle统一完成，避免部分提交。
        if (result == null || result.status() == null || !Set.of("GENERATED", "REFUSED", "INVALID").contains(result.status()) || result.inputTokens() < 0 || result.outputTokens() < 0
                || result.measurement() == null || !Set.of("ACTUAL", "ESTIMATED").contains(result.measurement()))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        confirmResponse(operationId, dispatchWorker, "GENERATE", result);
    }

    /** 评审是另一笔物理调用，单独确认结果；模型意见经过端口契约验证，不能直接批准候选。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReflectionResponse(String operationId, String dispatchWorker, SampleReflectionPort.Result result) {
        // Critic响应与Actor响应使用不同operationKind，防止同一候选的评审结果误写进生成字段。
        if (result == null) throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        confirmResponse(operationId, dispatchWorker, "REFLECT", result);
    }

    /** 只允许原发送者为匹配角色的未确认操作追加一次响应；失租时也不能修改批次检查点。 */
    private void confirmResponse(String operationId, String dispatchWorker, String kind, Object result) {
        // 条件更新确保同一物理操作只能被原发送者确认一次；迟到线程不能越权推进其他操作。
        int changed = operations.update(null, new LambdaUpdateWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getOperationId, operationId)
                .eq(AgentSampleOperationDO::getOperationKind, kind)
                .eq(AgentSampleOperationDO::getDispatchWorkerId, dispatchWorker).in(AgentSampleOperationDO::getStatus, "CALLING", "UNKNOWN")
                .set(AgentSampleOperationDO::getResultJson, json.write(result)).set(AgentSampleOperationDO::getStatus, "SUCCEEDED"));
        if (changed != 1) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
    }

    /** 原发送者未能确认响应时将CALLING记为UNKNOWN；不删除固定命令、不释放预算、不补发请求。 */
    public void recordUnknown(String operationId, String worker) {
        // UNKNOWN表示平台无法确认远端是否执行，不是“模型调用失败且可以安全重试”。
        operations.update(null, new LambdaUpdateWrapper<AgentSampleOperationDO>().eq(AgentSampleOperationDO::getOperationId, operationId)
                .eq(AgentSampleOperationDO::getDispatchWorkerId, worker).eq(AgentSampleOperationDO::getStatus, "CALLING").set(AgentSampleOperationDO::getStatus, "UNKNOWN"));
    }

    /** 暂停批次推进并保留在途操作；BLOCKED不是终态，迟到的已确认响应仍可在以后结算。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void block(String runId, String worker) {
        // BLOCKED把风险留在可审计状态；它不释放调用预算，也不自动创建第二个相同请求。
        var r = owned(runId, worker);
        if ("BLOCKED".equals(r.getStatus())) return;
        r.setStatus("BLOCKED");
        r.setErrorCode("GENERATION_RESULT_UNKNOWN");
        save(r);
    }

    /** 明确尚未调用时释放预留。已CALLING/UNKNOWN不允许被此入口伪装成零消耗。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelAccepted(String runId, String worker, String errorCode) {
        // 只有明确尚未发送的ACCEPTED操作可以取消并释放预留，CALLING/UNKNOWN必须保留其不确定事实。
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
     * 结算一个物理调用：由Reflection Agent决定下一步，再原子保存候选、报告、账目和检查点。
     * <p>生成的硬规则通过后先保存REFLECTING，下一笔操作才调用Critic；只有综合PASS才能去重并待审。
     * 评审不会再创建一条候选，而是更新同一轮候选的质量报告并递增版本。每次改写仍创建新候选。</p>
     * @param runId 待结算批次，其pendingOperationId必须指向当前槽位的SUCCEEDED操作
     * @param worker 当前有效租约持有者，不一定是原请求发送者
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void settle(String runId, String worker) {
        // settle是单次物理调用的唯一结算入口：候选、质量报告、Token账本和下一阶段在同一事务中推进。
        var r = owned(runId, worker);
        var op = pending(r);
        if (!"SUCCEEDED".equals(op.getStatus())) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        var task = production.authorizedTask(r.getTenantId(), r.getActorId(), r.getTaskId());
        var spec = production.spec(task);
        var policy = production.budget(task);
        var settlement = phase(r) == SampleReflectionAgent.Phase.REFLECT
                ? settleReflection(r, op, spec) : settleGeneration(r, op, spec);
        var c = settlement.candidate();
        var decision = settlement.decision();
        var usage = json.read(r.getUsageJson(), ProductionBudget.Usage.class);
        long tokens = Math.addExact(usage.tokens(), Math.addExact(settlement.inputTokens(), settlement.outputTokens()));
        if ("PASS".equals(decision.report().outcome())) deduplicate(c);
        r.setPendingOperationId(null);
        r.setUsageJson(json.write(new ProductionBudget.Usage(usage.agentCalls(), tokens, usage.reservedTokens() - op.getReservationTokens())));
        // Agent唯一决定阶段和改写资格；此处只落库，不再独立判断maxRewrites或运行第二套改写循环。
        switch (decision.next()) {
            case REFLECT -> r.setPhase(SampleReflectionAgent.Phase.REFLECT.name());
            case REWRITE -> {
                r.setPhase(SampleReflectionAgent.Phase.GENERATE.name());
                r.setParentCandidateId(c.getCandidateId());
                r.setRewriteRound(r.getRewriteRound() + 1);
            }
            case NEXT_SLOT -> {
                r.setPhase(SampleReflectionAgent.Phase.GENERATE.name());
                r.setOrdinal(r.getOrdinal() + 1);
                r.setRewriteRound(0);
                r.setParentCandidateId(null);
            }
        }
        // 无论Agent建议什么，工程边界优先；下次生成或评审都还要单独经过prepare预留检查。
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

    /** 生成响应落成不可变正文候选。硬规则未通过不调用Critic，拒绝/非法生成也保留记录和消耗。 */
    private Settlement settleGeneration(AgentSampleRunDO r, AgentSampleOperationDO op, GenerationSpec spec) {
        // 生成阶段总是形成一个候选事实，即使结果是拒绝或非法；这样可以完整审计失败原因和实际用量。
        var result = json.read(op.getResultJson(), SampleGenerationPort.Result.class);
        var decision = SampleReflectionAgent.VERSION.equals(r.getWorkflowVersion())
                ? agent.afterGeneration(spec, r.getRewriteRound(), result) : agent.settleLegacy(spec, result);
        var c = new AgentSampleCandidateDO();
        // 由操作身份导出候选身份，配合事务和唯一键避免恢复时为同一响应生成多个候选。
        c.setCandidateId("candidate_" + json.digest(op.getOperationId()).substring(0, 32));
        c.setTaskId(r.getTaskId());
        c.setRunId(r.getRunId());
        c.setTenantId(r.getTenantId());
        c.setActorId(r.getActorId());
        c.setOperationId(op.getOperationId());
        c.setOrdinal(op.getOrdinal());
        c.setRewriteRound(op.getRewriteRound());
        c.setParentCandidateId(op.getParentCandidateId());
        c.setSeedId(spec.seedId());
        c.setContent(result.content());
        c.setContentHash(result.content() == null ? null : json.digest(result.content()));
        c.setQualityJson(json.write(decision.report()));
        c.setStatus(candidateStatus(decision));
        c.setVersion(0L);
        c.setCreatedAt(now());
        candidates.insert(c);
        return new Settlement(c, decision, result.inputTokens(), result.outputTokens());
    }

    /** 评审只更新对应生成候选的质量，不覆盖正文；批次行锁和候选状态共同阻止重复结算。 */
    private Settlement settleReflection(AgentSampleRunDO r, AgentSampleOperationDO op, GenerationSpec spec) {
        // 评审阶段不生成新正文，只更新当前候选的质量报告和控制版本。
        var c = currentCandidate(r);
        var result = json.read(op.getResultJson(), SampleReflectionPort.Result.class);
        var decision = agent.afterReflection(spec, r.getRewriteRound(), c.getContent(), result);
        c.setQualityJson(json.write(decision.report()));
        c.setStatus(candidateStatus(decision));
        c.setVersion(c.getVersion() + 1);
        candidates.updateById(c);
        return new Settlement(c, decision, result.inputTokens(), result.outputTokens());
    }

    /** 明确区分尚未评审与质量通过；达到改写上限不会进入待人工审核。 */
    private String candidateStatus(SampleReflectionAgent.Decision decision) {
        if (decision.next() == SampleReflectionAgent.Next.REFLECT) return "REFLECTING";
        return "PASS".equals(decision.report().outcome()) ? "PENDING_REVIEW" : "QUALITY_REJECTED";
    }

    /** 事务内结算中间值，不作为API或数据库JSON结构。 */
    private record Settlement(AgentSampleCandidateDO candidate, SampleReflectionAgent.Decision decision,
                              long inputTokens, long outputTokens) { }

    /** 跨批次规范精确去重，不冒充语义去重；竞争唯一键失败只查胜者，不重复生成。 */
    private void deduplicate(AgentSampleCandidateDO c) {
        // 去重只在综合PASS后执行；它是规范化文本的精确匹配，不是语义相似度或风险归类。
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
        // 没有待结算操作时才能收口失败或停止，避免终态掩盖远端调用结果未知的问题。
        var r = owned(runId, worker);
        if (r.getPendingOperationId() != null || !Set.of("FAILED", "STOPPED").contains(status))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        terminal(r, status, reason);
        save(r);
    }

    /**
     * 人工请求停止新增生成及评审，expectedVersion来自批次；状态变更与命令回执同事务保存。
     * 有在途操作时仅记录意图，不能承诺取消供应商请求；无待结算操作时立即转STOPPED。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Control stop(String tenant, String actor, String runId, FactoryRequests.Stop request) {
        // stop只记录“停止新增调用”的意图；已经进入CALLING的供应商请求不承诺可撤回。
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
        var result = new FactoryResponses.Control(runId, r.getVersion(), null, true, "停止新增生成与评审，保留在途和迟到事实");
        production.saveControl(runId, request.idempotencyKey(), hash, result);
        return result;
    }

    /** 按批次当前阶段生成固定操作身份；生成ID保持旧算法，评审使用独立命名空间。 */
    public String operationId(AgentSampleRunDO r) {
        return operationId(r.getRunId(), r.getOrdinal(), r.getRewriteRound(), phase(r));
    }

    /** 相同槽位同轮次的Actor与Critic永不共用操作ID，查询历史评审时也使用相同算法。 */
    public String operationId(String runId, int ordinal, int rewriteRound, SampleReflectionAgent.Phase phase) {
        String source = runId + ":" + ordinal + ":" + rewriteRound;
        return phase == SampleReflectionAgent.Phase.GENERATE
                ? "genop_" + json.digest(source).substring(0, 32)
                : "refop_" + json.digest(source + ":REFLECT").substring(0, 32);
    }

    /** 严格恢复持久化阶段，不用默认GENERATE掩盖未知或损坏的阶段。 */
    public SampleReflectionAgent.Phase phase(AgentSampleRunDO run) {
        // 阶段是持久化检查点的一部分；未知阶段直接报持久化错误，不能默认按GENERATE继续。
        // workflowVersion描述架构，不是Actor提示词版本；v2提示词升级不能使v1历史批次失去恢复资格。
        if (!SampleReflectionAgent.VERSION.equals(run.getWorkflowVersion()) && !SampleGenerationWorkflow.LEGACY_VERSION.equals(run.getWorkflowVersion()))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        if ("GENERATE".equals(run.getPhase())) return SampleReflectionAgent.Phase.GENERATE;
        if ("REFLECT".equals(run.getPhase()) && SampleReflectionAgent.VERSION.equals(run.getWorkflowVersion()))
            return SampleReflectionAgent.Phase.REFLECT;
        throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
    }

    /** 读取同槽位同轮的待评审候选并核对正文、父链和生成响应，不允许借其他候选完成评审。 */
    public AgentSampleCandidateDO currentCandidate(AgentSampleRunDO run) {
        // REFLECT只能评审当前槽位当前轮次生成的候选，不能按seedId或“最新候选”模糊关联。
        if (phase(run) != SampleReflectionAgent.Phase.REFLECT) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        String generationId = operationId(run.getRunId(), run.getOrdinal(), run.getRewriteRound(), SampleReflectionAgent.Phase.GENERATE);
        var c = candidates.selectOne(new LambdaQueryWrapper<AgentSampleCandidateDO>()
                .eq(AgentSampleCandidateDO::getOperationId, generationId).eq(AgentSampleCandidateDO::getRunId, run.getRunId())
                .eq(AgentSampleCandidateDO::getTenantId, run.getTenantId()).eq(AgentSampleCandidateDO::getActorId, run.getActorId()));
        if (c == null || !"REFLECTING".equals(c.getStatus()) || !run.getOrdinal().equals(c.getOrdinal())
                || !run.getRewriteRound().equals(c.getRewriteRound()) || !Objects.equals(run.getParentCandidateId(), c.getParentCandidateId())
                || c.getContent() == null || !Objects.equals(c.getContentHash(), json.digest(c.getContent())))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        var op = operation(generationId);
        if (!run.getRunId().equals(op.getRunId()) || !run.getOrdinal().equals(op.getOrdinal())
                || !run.getRewriteRound().equals(op.getRewriteRound()) || !Objects.equals(run.getParentCandidateId(), op.getParentCandidateId())
                || !"GENERATE".equals(op.getOperationKind()) || !"SUCCEEDED".equals(op.getStatus())
                || !Objects.equals(json.read(op.getResultJson(), SampleGenerationPort.Result.class).content(), c.getContent()))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return c;
    }

    /** 恢复/结算前校验固定操作归属当前检查点，不能借其他批次或轮次的回执推进。 */
    public AgentSampleOperationDO pending(AgentSampleRunDO run) {
        // pending是检查点关联的固定操作；任何批次、槽位、轮次或角色不一致都视为数据损坏。
        if (run.getPendingOperationId() == null) throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        var op = operation(run.getPendingOperationId());
        if (!run.getRunId().equals(op.getRunId()) || !run.getOrdinal().equals(op.getOrdinal()) || !run.getRewriteRound().equals(op.getRewriteRound())
                || !Objects.equals(run.getParentCandidateId(), op.getParentCandidateId()) || !operationId(run).equals(op.getOperationId())
                || !phase(run).name().equals(op.getOperationKind()))
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

    /** 结束时将未完成反思的候选明确拒绝，不能遗留一个永远待评审的候选；与批次终态同事务提交。 */
    private void terminal(AgentSampleRunDO r, String status, String reason) {
        if ("REFLECT".equals(r.getPhase()) && r.getPendingOperationId() == null) {
            var c = currentCandidate(r);
            c.setStatus("QUALITY_REJECTED");
            c.setQualityJson(json.write(new SampleQualityWorkflow.Report(SampleReflectionAgent.QUALITY_VERSION,
                    "REJECT", List.of("模型反思评审未完成，批次结束：" + reason))));
            c.setVersion(c.getVersion() + 1);
            candidates.updateById(c);
        }
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
                .set(AgentSampleRunDO::getPhase, r.getPhase())
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
