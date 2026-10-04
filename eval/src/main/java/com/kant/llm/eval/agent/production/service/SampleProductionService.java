package com.kant.llm.eval.agent.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.agent.contract.production.*;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.production.dao.entity.*;
import com.kant.llm.eval.agent.production.dao.mapper.*;
import com.kant.llm.eval.agent.production.dto.*;
import com.kant.llm.eval.agent.production.error.SampleFactoryErrorCode;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/** 种子、不可变生产计划、手动批次、计划控制与调度受理；本类绝不调用生成模型或被测目标。 */
@Service
public class SampleProductionService {
    private final AgentSampleSeedMapper seeds;
    private final AgentSampleTaskMapper tasks;
    private final AgentSampleRunMapper runs;
    private final AgentSampleCommandMapper commands;
    private final AgentSampleScheduleFireMapper fires;
    private final AgentSampleRunReceiptMapper receipts;
    private final ProbePrincipalProvider principals;
    private final SampleGenerationPort generator;
    private final FactoryJsonSupport json;

    public SampleProductionService(AgentSampleSeedMapper seeds, AgentSampleTaskMapper tasks, AgentSampleRunMapper runs,
                                   AgentSampleCommandMapper commands, AgentSampleScheduleFireMapper fires, ProbePrincipalProvider principals,
                                   SampleGenerationPort generator, FactoryJsonSupport json, AgentSampleRunReceiptMapper receipts) {
        this.seeds = seeds;
        this.tasks = tasks;
        this.runs = runs;
        this.commands = commands;
        this.fires = fires;
        this.principals = principals;
        this.generator = generator;
        this.json = json;
        this.receipts = receipts;
    }

    public SampleGenerationPort.Profile profile() {
        principals.currentPrincipal();
        return generator.profile();
    }

    /** 专家种子是独立不可变资产，修改时创建新seedId，而不是覆盖已进入批次的原文。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Seed createSeed(FactoryRequests.Seed request) {
        var p = principals.currentPrincipal();
        String hash = json.hash(request);
        var old = seeds.selectOne(new LambdaQueryWrapper<AgentSampleSeedDO>().eq(AgentSampleSeedDO::getTenantId, p.tenantId())
                .eq(AgentSampleSeedDO::getActorId, p.actorId()).eq(AgentSampleSeedDO::getIdempotencyKey, request.idempotencyKey()));
        if (old != null) {
            same(hash, old.getRequestHash());
            return seedResponse(old);
        }
        var row = new AgentSampleSeedDO();
        row.setSeedId(id("seed_"));
        row.setTenantId(p.tenantId());
        row.setActorId(p.actorId());
        row.setIdempotencyKey(request.idempotencyKey());
        row.setRequestHash(hash);
        row.setName(request.name());
        row.setContent(request.content());
        row.setContentHash(json.digest(request.content()));
        row.setSourceRef(request.sourceRef());
        row.setCreatedAt(now());
        seeds.insert(row);
        return seedResponse(row);
    }

    /** 生成配置快照与独立预算先冻结；创建任务不会启用计划或发出模型请求。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Task createTask(FactoryRequests.Task request) {
        var p = principals.currentPrincipal();
        String hash = json.hash(request);
        var old = tasks.selectOne(new LambdaQueryWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getTenantId, p.tenantId())
                .eq(AgentSampleTaskDO::getActorId, p.actorId()).eq(AgentSampleTaskDO::getIdempotencyKey, request.idempotencyKey()));
        if (old != null) {
            same(hash, old.getRequestHash());
            return taskResponse(old);
        }
        seed(p.tenantId(), p.actorId(), request.spec().seedId());
        requireProfile(request.spec());
        if (request.budget().maxAgentCalls() < request.spec().quantity())
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        var row = new AgentSampleTaskDO();
        row.setTaskId(id("sptask_"));
        row.setTenantId(p.tenantId());
        row.setActorId(p.actorId());
        row.setIdempotencyKey(request.idempotencyKey());
        row.setRequestHash(hash);
        row.setName(request.name());
        row.setActivityId(request.activityId());
        row.setAuthorizationRef(request.authorizationRef());
        row.setSpecJson(json.write(request.spec()));
        row.setBudgetJson(json.write(request.budget()));
        row.setScheduleJson(request.schedule() == null ? null : json.write(request.schedule()));
        row.setScheduleEnabled(false);
        row.setNextFireAt(request.schedule() == null ? null : json.utc(request.schedule().at(request.schedule().startDate())));
        row.setVersion(0L);
        row.setCreatedAt(now());
        tasks.insert(row);
        return taskResponse(row);
    }

    /**
     * 重置生产计划不是覆盖旧计划，而是创建一个新的冻结计划。
     *
     * 旧计划可能引用应用重启前的生成角色快照；重置时保留原种子、变异、风险、预算和调度定义，
     * 只重新读取当前生成角色并写入新的generatorProfileRef。新计划默认不启用定时任务，避免重置动作
     * 意外补发旧计划的调度槽位；原计划和原批次仍可继续审计。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Task resetTask(String sourceTaskId, FactoryRequests.ResetTask request) {
        var p = principals.currentPrincipal();
        var source = authorizedTask(p.tenantId(), p.actorId(), sourceTaskId);
        String resetHash = json.hash(new ResetTaskIdentity(sourceTaskId, request.idempotencyKey(), request.name()));

        // 先处理幂等回放；即使当前模型配置后来不可用，也应返回首次创建的新计划。
        var old = tasks.selectOne(new LambdaQueryWrapper<AgentSampleTaskDO>()
                .eq(AgentSampleTaskDO::getTenantId, p.tenantId())
                .eq(AgentSampleTaskDO::getActorId, p.actorId())
                .eq(AgentSampleTaskDO::getIdempotencyKey, request.idempotencyKey()));
        if (old != null) {
            same(resetHash, old.getRequestHash());
            return taskResponse(old);
        }

        var profile = generator.profile();
        if (!profile.available()) throw error(SampleFactoryErrorCode.GENERATOR_UNAVAILABLE);

        var sourceSpec = spec(source);
        var resetSpec = new GenerationSpec(sourceSpec.seedId(), sourceSpec.seedVersion(), profile.profileRef(),
                sourceSpec.promptVersion(), sourceSpec.riskId(), sourceSpec.ruleSetVersion(), sourceSpec.quantity(),
                sourceSpec.mutations(), sourceSpec.topicTerms(), sourceSpec.minLength(), sourceSpec.maxLength(),
                sourceSpec.maxRewrites(), sourceSpec.preconditions(), sourceSpec.assertionDescription());
        var sourceBudget = budget(source);
        var sourceSchedule = schedule(source);
        seed(p.tenantId(), p.actorId(), resetSpec.seedId());

        var row = new AgentSampleTaskDO();
        row.setTaskId(id("sptask_"));
        row.setTenantId(p.tenantId());
        row.setActorId(p.actorId());
        row.setIdempotencyKey(request.idempotencyKey());
        row.setRequestHash(resetHash);
        row.setName(request.name());
        row.setActivityId(source.getActivityId());
        row.setAuthorizationRef(source.getAuthorizationRef());
        row.setSpecJson(json.write(resetSpec));
        row.setBudgetJson(json.write(sourceBudget));
        row.setScheduleJson(sourceSchedule == null ? null : json.write(sourceSchedule));
        row.setScheduleEnabled(false);
        row.setNextFireAt(sourceSchedule == null ? null : json.utc(sourceSchedule.at(sourceSchedule.startDate())));
        row.setVersion(0L);
        row.setCreatedAt(now());
        tasks.insert(row);
        return taskResponse(row);
    }

    /** 手动运行或补跑均使用新批次；精确计划槽位唯一，补跑不能覆盖原批次。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Run start(String taskId, FactoryRequests.Run request) {
        var p = principals.currentPrincipal();
        authorizedTask(p.tenantId(), p.actorId(), taskId);
        var task = lockTask(taskId);
        String hash = json.hash(request);
        var old = receipts.selectOne(new LambdaQueryWrapper<AgentSampleRunReceiptDO>().eq(AgentSampleRunReceiptDO::getTaskId, taskId)
                .eq(AgentSampleRunReceiptDO::getIdempotencyKey, request.idempotencyKey()));
        if (old != null) {
            same(hash, old.getRequestHash());
            return json.read(old.getResultJson(), FactoryResponses.Run.class);
        }
        if (task.getVersion() != request.expectedVersion().longValue())
            throw error(SampleFactoryErrorCode.VERSION_CONFLICT);
        requireProfile(spec(task));
        if (request.plannedFor() != null && (schedule(task) == null || !schedule(task).isSlot(request.plannedFor()) || request.plannedFor().isAfter(Instant.now())))
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        String trigger = request.plannedFor() == null ? "manual:" + request.idempotencyKey() : "slot:" + request.plannedFor();
        // 即使另一个补跑请求已创建原槽位，也保存当前请求键的回执，避免同键随后偷换槽位。
        var result = runResponse(queue(task, "manual:" + request.idempotencyKey(), hash, trigger, request.plannedFor() == null ? "MANUAL" : "BACKFILL", request.plannedFor()));
        var receipt = new AgentSampleRunReceiptDO();
        receipt.setTaskId(taskId);
        receipt.setIdempotencyKey(request.idempotencyKey());
        receipt.setRequestHash(hash);
        receipt.setRunId(result.runId());
        receipt.setResultJson(json.write(result));
        receipts.insert(receipt);
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Control controlSchedule(String taskId, FactoryRequests.ScheduleControl request) {
        var p = principals.currentPrincipal();
        authorizedTask(p.tenantId(), p.actorId(), taskId);
        var task = lockTask(taskId);
        String hash = json.hash(request);
        var old = controlReplay(taskId, request.idempotencyKey(), hash);
        if (old != null) return old;
        if (task.getVersion() != request.expectedVersion().longValue())
            throw error(SampleFactoryErrorCode.VERSION_CONFLICT);
        if (schedule(task) == null || request.enabled() && task.getNextFireAt() == null)
            throw error(SampleFactoryErrorCode.INVALID_REQUEST);
        if (request.enabled()) requireProfile(spec(task));
        task.setScheduleEnabled(request.enabled());
        task.setVersion(task.getVersion() + 1);
        tasks.updateById(task);
        var result = new FactoryResponses.Control(taskId, task.getVersion(), request.enabled(), null, request.reason());
        saveControl(taskId, request.idempotencyKey(), hash, result);
        return result;
    }

    /** 调度器只读取持久化到期计划，无认证线程假设；租户/操作者来自原已批准任务。 */
    public List<AgentSampleTaskDO> due(Instant at) {
        return tasks.selectList(new LambdaQueryWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getScheduleEnabled, true)
                .le(AgentSampleTaskDO::getNextFireAt, json.utc(at)).orderByAsc(AgentSampleTaskDO::getNextFireAt).last("LIMIT 20"));
    }

    /** 同一任务行锁 + 批次槽位唯一索引 + 触发审计，保证多个实例不重复生成相同批次。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void triggerScheduled(String taskId, Instant at) {
        var task = lockTask(taskId);
        if (!Boolean.TRUE.equals(task.getScheduleEnabled()) || task.getNextFireAt() == null) return;
        Instant planned = json.instant(task.getNextFireAt());
        if (planned.isAfter(at)) return;
        var schedule = schedule(task);
        requireProfile(spec(task));
        if (schedule.misfirePolicy() == ProductionSchedule.MisfirePolicy.FIRE_LATEST) {
            Instant latest = schedule.latestDue(at);
            if (latest == null) return;
            if (latest.isAfter(planned)) {
                fire(task, planned, "MISSED_RANGE", null, Map.of("from", planned.toString(), "before", latest.toString(), "zoneId", schedule.zoneId()));
                planned = latest;
            }
        }
        if (schedule.misfirePolicy() == ProductionSchedule.MisfirePolicy.SKIP && planned.plusSeconds(schedule.graceSeconds()).isBefore(at))
            fire(task, planned, "MISSED", null, Map.of("zoneId", schedule.zoneId(), "reason", "超出计划宽限，未调用生成模型"));
        else {
            String key = "scheduled:" + planned;
            var run = queue(task, key, json.hash(key), "slot:" + planned, "SCHEDULED", planned);
            fire(task, planned, "QUEUED", run.getRunId(), Map.of("zoneId", schedule.zoneId(), "misfirePolicy", schedule.misfirePolicy().name()));
        }
        Instant next = schedule.next(planned);
        var update = new LambdaUpdateWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getId, task.getId())
                .set(AgentSampleTaskDO::getNextFireAt, json.utc(next));
        if (next == null) update.set(AgentSampleTaskDO::getScheduleEnabled, false).setSql("version = version + 1");
        tasks.update(null, update);
    }

    private AgentSampleRunDO queue(AgentSampleTaskDO task, String key, String hash, String trigger, String kind, Instant planned) {
        var old = runs.selectOne(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getTaskId, task.getTaskId()).eq(AgentSampleRunDO::getTriggerKey, trigger));
        if (old != null) return old;
        var row = new AgentSampleRunDO();
        row.setRunId(id("sprun_"));
        row.setTaskId(task.getTaskId());
        row.setTenantId(task.getTenantId());
        row.setActorId(task.getActorId());
        row.setIdempotencyKey(key);
        row.setRequestHash(hash);
        row.setTriggerKey(trigger);
        row.setTriggerKind(kind);
        row.setPlannedFor(json.utc(planned));
        row.setStatus("QUEUED");
        row.setVersion(0L);
        row.setUsageJson(json.write(new ProductionBudget.Usage(0, 0, 0)));
        row.setOrdinal(0);
        row.setRewriteRound(0);
        row.setStopRequested(false);
        row.setCreatedAt(now());
        row.setDeadlineAt(row.getCreatedAt().plusSeconds(budget(task).maxDurationSeconds()));
        runs.insert(row);
        return row;
    }

    private void fire(AgentSampleTaskDO task, Instant planned, String kind, String runId, Map<String, String> details) {
        var old = fires.selectOne(new LambdaQueryWrapper<AgentSampleScheduleFireDO>().eq(AgentSampleScheduleFireDO::getTaskId, task.getTaskId()).eq(AgentSampleScheduleFireDO::getPlannedFor, json.utc(planned)));
        if (old != null) return;
        var row = new AgentSampleScheduleFireDO();
        row.setTaskId(task.getTaskId());
        row.setPlannedFor(json.utc(planned));
        row.setKind(kind);
        row.setRunId(runId);
        row.setDetailsJson(json.write(details));
        row.setCreatedAt(now());
        fires.insert(row);
    }

    public FactoryResponses.Seed seed(String id) {
        var p = principals.currentPrincipal();
        return seedResponse(seed(p.tenantId(), p.actorId(), id));
    }

    public FactoryResponses.Content seedContent(String id) {
        var p = principals.currentPrincipal();
        var seed = seed(p.tenantId(), p.actorId(), id);
        return new FactoryResponses.Content(id, seed.getContent(), seed.getContentHash());
    }

    public FactoryResponses.Task task(String id) {
        var p = principals.currentPrincipal();
        return taskResponse(authorizedTask(p.tenantId(), p.actorId(), id));
    }

    public List<FactoryResponses.Task> tasks() {
        var p = principals.currentPrincipal();
        return tasks.selectList(new LambdaQueryWrapper<AgentSampleTaskDO>()
                .eq(AgentSampleTaskDO::getTenantId, p.tenantId()).eq(AgentSampleTaskDO::getActorId, p.actorId()).orderByDesc(AgentSampleTaskDO::getId).last("LIMIT 100")).stream().map(this::taskResponse).toList();
    }

    public List<FactoryResponses.Seed> seeds() {
        var p = principals.currentPrincipal();
        return seeds.selectList(new LambdaQueryWrapper<AgentSampleSeedDO>()
                .eq(AgentSampleSeedDO::getTenantId, p.tenantId()).eq(AgentSampleSeedDO::getActorId, p.actorId()).orderByDesc(AgentSampleSeedDO::getId).last("LIMIT 100")).stream().map(this::seedResponse).toList();
    }

    public List<FactoryResponses.Fire> fires(String taskId) {
        var p = principals.currentPrincipal();
        authorizedTask(p.tenantId(), p.actorId(), taskId);
        return fires.selectList(new LambdaQueryWrapper<AgentSampleScheduleFireDO>().eq(AgentSampleScheduleFireDO::getTaskId, taskId).orderByDesc(AgentSampleScheduleFireDO::getId).last("LIMIT 100"))
                .stream().map(f -> new FactoryResponses.Fire(json.instant(f.getPlannedFor()), f.getKind(), f.getRunId(), f.getDetailsJson(), json.instant(f.getCreatedAt()))).toList();
    }

    public List<FactoryResponses.Run> runs(String taskId) {
        var p = principals.currentPrincipal();
        authorizedTask(p.tenantId(), p.actorId(), taskId);
        return runs.selectList(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getTaskId, taskId).orderByDesc(AgentSampleRunDO::getId).last("LIMIT 100")).stream().map(this::runResponse).toList();
    }

    public AgentSampleTaskDO authorizedTask(String tenant, String actor, String id) {
        var row = tasks.selectOne(new LambdaQueryWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getTaskId, id).eq(AgentSampleTaskDO::getTenantId, tenant).eq(AgentSampleTaskDO::getActorId, actor));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return row;
    }

    public AgentSampleSeedDO seed(String tenant, String actor, String id) {
        var row = seeds.selectOne(new LambdaQueryWrapper<AgentSampleSeedDO>().eq(AgentSampleSeedDO::getSeedId, id).eq(AgentSampleSeedDO::getTenantId, tenant).eq(AgentSampleSeedDO::getActorId, actor));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        if (!json.digest(row.getContent()).equals(row.getContentHash()))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return row;
    }

    public GenerationSpec spec(AgentSampleTaskDO task) {
        return json.read(task.getSpecJson(), GenerationSpec.class);
    }

    public ProductionBudget budget(AgentSampleTaskDO task) {
        return json.read(task.getBudgetJson(), ProductionBudget.class);
    }

    public ProductionSchedule schedule(AgentSampleTaskDO task) {
        return task.getScheduleJson() == null ? null : json.read(task.getScheduleJson(), ProductionSchedule.class);
    }

    public FactoryResponses.Run runResponse(AgentSampleRunDO r) {
        return new FactoryResponses.Run(r.getRunId(), r.getTaskId(), r.getStatus(), r.getVersion(), r.getTriggerKind(),
                json.instant(r.getPlannedFor()), r.getOrdinal(), r.getRewriteRound(), r.getPendingOperationId(), json.read(r.getUsageJson(), ProductionBudget.Usage.class),
                Boolean.TRUE.equals(r.getStopRequested()), r.getErrorCode(), json.instant(r.getCreatedAt()), json.instant(r.getDeadlineAt()), json.instant(r.getFinishedAt()));
    }

    public FactoryResponses.Control controlReplay(String resource, String key, String hash) {
        var old = commands.selectOne(new LambdaQueryWrapper<AgentSampleCommandDO>().eq(AgentSampleCommandDO::getResourceId, resource).eq(AgentSampleCommandDO::getIdempotencyKey, key));
        if (old == null) return null;
        same(hash, old.getRequestHash());
        return json.read(old.getResultJson(), FactoryResponses.Control.class);
    }

    public void saveControl(String resource, String key, String hash, FactoryResponses.Control result) {
        var row = new AgentSampleCommandDO();
        row.setResourceId(resource);
        row.setIdempotencyKey(key);
        row.setRequestHash(hash);
        row.setResultJson(json.write(result));
        commands.insert(row);
    }

    private AgentSampleTaskDO lockTask(String id) {
        var t = tasks.selectOne(new LambdaQueryWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getTaskId, id).last("LIMIT 1 FOR UPDATE"));
        if (t == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return t;
    }

    private void requireProfile(GenerationSpec spec) {
        var profile = generator.profile();
        if (!profile.available() || !profile.profileRef().equals(spec.generatorProfileRef()))
            throw error(SampleFactoryErrorCode.GENERATOR_UNAVAILABLE);
    }

    private FactoryResponses.Seed seedResponse(AgentSampleSeedDO s) {
        return new FactoryResponses.Seed(s.getSeedId(), 1, s.getName(), s.getContentHash(), s.getSourceRef(), json.instant(s.getCreatedAt()));
    }

    private FactoryResponses.Task taskResponse(AgentSampleTaskDO t) {
        return new FactoryResponses.Task(t.getTaskId(), "SAMPLE_PRODUCTION", t.getName(), t.getActivityId(), t.getAuthorizationRef(),
                spec(t), budget(t), schedule(t), Boolean.TRUE.equals(t.getScheduleEnabled()), json.instant(t.getNextFireAt()), t.getVersion(), json.instant(t.getCreatedAt()));
    }

    private void same(String expected, String actual) {
        if (!expected.equals(actual)) throw error(SampleFactoryErrorCode.IDEMPOTENCY_CONFLICT);
    }

    private String id(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private LocalDateTime now() {
        return json.utc(Instant.now());
    }

    private ClientException error(SampleFactoryErrorCode code) {
        return new ClientException(code.message(), code);
    }

    /** 重置请求的幂等摘要同时绑定来源计划，避免同一个请求键被错误回放到另一计划。 */
    private record ResetTaskIdentity(String sourceTaskId, String idempotencyKey, String name) {
    }
}
