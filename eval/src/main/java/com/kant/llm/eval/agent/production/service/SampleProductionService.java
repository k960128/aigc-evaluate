package com.kant.llm.eval.agent.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.agent.contract.production.*;
import com.kant.agent.service.production.SampleGenerationWorkflow;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.production.config.SampleFactoryModelConfiguration;
import com.kant.llm.eval.agent.production.dao.entity.*;
import com.kant.llm.eval.agent.production.dao.mapper.*;
import com.kant.llm.eval.agent.production.dto.*;
import com.kant.llm.eval.agent.production.error.SampleFactoryErrorCode;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/**
 * D05生产任务的受理层：保存专家种子、冻结生产计划，并将手动或定时请求转换为待执行批次。
 * <p>计划定义与运行事实分开存储：一个计划可以创建多个批次，重置计划则创建新的计划标识，
 * 不覆盖历史配置。本类只读取生成/评审的组合配置，不调用模型或被测目标；模型调用由Runner负责。</p>
 * <p>HTTP写操作先校验主体，再处理幂等回放，最后执行版本和业务检查。
 * 后台调度没有登录线程上下文，使用数据库计划中保存的归属信息。</p>
 */
@Service
public class SampleProductionService {
    /** 不可变专家种子及正文摘要。 */
    private final AgentSampleSeedMapper seeds;
    /** 冻结配置、调度开关及调度游标。 */
    private final AgentSampleTaskMapper tasks;
    /** 每次生产的独立进度与预算账本。 */
    private final AgentSampleRunMapper runs;
    /** 计划启停及批次停止的首次控制回执。 */
    private final AgentSampleCommandMapper commands;
    /** 定时触发、单槽位遗漏及遗漏区间的审计记录。 */
    private final AgentSampleScheduleFireMapper fires;
    /** 手动运行或补跑请求对应的首次受理快照。 */
    private final AgentSampleRunReceiptMapper receipts;
    /** 从服务端登录上下文提取主体，客户端不能指定租户或操作者。 */
    private final ProbePrincipalProvider principals;
    /** 组合快照同时冻结Actor与Critic；任一角色变更都不能静默替换旧计划。 */
    private final SampleFactoryModelConfiguration models;
    /** 持久化JSON、摘要与UTC微秒时间转换。 */
    private final FactoryJsonSupport json;

    /** 注入持久化与身份依赖；不在构造阶段访问数据库或调用模型。 */
    public SampleProductionService(AgentSampleSeedMapper seeds, AgentSampleTaskMapper tasks, AgentSampleRunMapper runs,
                                   AgentSampleCommandMapper commands, AgentSampleScheduleFireMapper fires, ProbePrincipalProvider principals,
                                   SampleFactoryModelConfiguration models, FactoryJsonSupport json, AgentSampleRunReceiptMapper receipts) {
        this.seeds = seeds;
        this.tasks = tasks;
        this.runs = runs;
        this.commands = commands;
        this.fires = fires;
        this.principals = principals;
        this.models = models;
        this.json = json;
        this.receipts = receipts;
    }

    /** 返回当前主体可见的组合及单角色配置；available要求两个角色均可用，但不是健康检查。 */
    public FactoryResponses.ModelProfile profile() {
        // 该接口只返回当前后端配置快照；available为false时，前端应阻止新计划和新批次受理。
        principals.currentPrincipal();
        return models.profile();
    }

    /**
     * 幂等创建不可变专家种子；同键同内容返回原种子，同键不同内容报冲突。
     * 正文按原样保存并计算摘要，不按去重规则改写。修订素材需要另建seedId。
     * @param request 名称、正文、可选来源引用和主体内创建幂等键
     * @return 不含正文的种子元数据，正文通过独立接口读取
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Seed createSeed(FactoryRequests.Seed request) {
        // 种子是专家输入，不是模型生成结果；创建种子本身不会进入Worker或消耗模型额度。
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

    /**
     * 创建冻结生产计划：校验种子归属及完整性、双角色组合快照与最低调用次数预算，保存当前Actor提示词版本。
     * 接收旧前端的v1提示词入参，但仅在当前组合快照校验通过后为新计划记录实际执行的v2，不改写历史计划。
     * 仅保存计划定义，不创建批次，不调用模型，也不自动开启定时任务。
     * @param request 专家选定的种子、生成约束、单批预算及可选每日计划
     * @return 新计划，或幂等命中计划的当前控制状态
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Task createTask(FactoryRequests.Task request) {
        // 计划创建阶段冻结所有影响生成结果的因素，后续执行不会重新读取请求或当前页面状态。
        var p = principals.currentPrincipal();
        String hash = json.hash(request);
        var old = tasks.selectOne(new LambdaQueryWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getTenantId, p.tenantId())
                .eq(AgentSampleTaskDO::getActorId, p.actorId()).eq(AgentSampleTaskDO::getIdempotencyKey, request.idempotencyKey()));
        if (old != null) {
            same(hash, old.getRequestHash());
            return taskResponse(old);
        }
        // 保留原最低次数门槛，允许有界尝试；Reflection正常通过通常需每槽位生成+评审两次。
        // 次数和Token都不会自动扩容，较小预算可能在评审前耗尽，不能承诺所有槽位都能完成。
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
        // 请求摘要保留原入参用于幂等冲突检查；冻结配置记录实际执行版本，响应也返回这份配置。
        row.setSpecJson(json.write(currentSpec(request.spec(), request.spec().generatorProfileRef())));
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
     * 旧计划可能引用变更前的模型角色快照；重置时保留原种子、变异、风险、预算和调度定义，
     * 重新读取生成与评审角色的组合身份并写入generatorProfileRef，同时冻结当前Actor提示词版本。新计划默认不启用定时任务，避免重置动作
     * 意外补发旧计划的调度槽位；原计划和原批次仍可继续审计。
     * <p>不修改旧计划的调度开关，不停止旧批次；旧预算也原样复制，不能用重置解决预算不足。
     * 新计划保留原日期范围，随后人工启用时仍按原漏触发策略处理。</p>
     * @param sourceTaskId 当前主体拥有的来源计划标识
     * @param request 新计划名称及创建键；与普通创建共享主体内幂等键空间
     * @return 新计划的当前状态；同键重试不会再次刷新已冻结的双角色组合快照
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Task resetTask(String sourceTaskId, FactoryRequests.ResetTask request) {
        // reset的语义是“接受最新角色配置后创建新计划”，不是修改旧计划或修复旧批次的历史事实。
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

        var profile = models.profile();
        if (!profile.available()) throw error(SampleFactoryErrorCode.GENERATOR_UNAVAILABLE);

        var sourceSpec = spec(source);
        var resetSpec = currentSpec(sourceSpec, profile.profileRef());
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

    /**
     * 受理一次手动生产或精确调度槽位补跑，不同步等待生成结果。
     * <p>首次请求先锁计划，同键重试回放首次受理结果；不同键的同槽位请求共用原批次，
     * 不覆盖原执行事实。当前进度应通过批次查询读取，不能根据首次回执判断。</p>
     * @param taskId 冻结生产计划标识
     * @param request 计划控制版本、幂等键及可选原UTC槽位；不填槽位表示手动运行
     * @return 本请求首次受理的批次快照
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Run start(String taskId, FactoryRequests.Run request) {
        // HTTP只负责快速受理并写入QUEUED；真正的模型调用由后台Worker异步完成。
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

    /**
     * 显式开启或关闭每日生产；版本来自计划而非批次，同键回放优先于版本检查。
     * 关闭只阻止后续调度，不停止已排队/在途批次；重新启用也不重置原调度游标。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FactoryResponses.Control controlSchedule(String taskId, FactoryRequests.ScheduleControl request) {
        // 启停只影响未来槽位，不能回滚已经创建的Run，也不能取消已经发送的模型请求。
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
        // 这里只做候选计划扫描，不在查询阶段改变游标；游标推进必须发生在triggerScheduled事务内。
        return tasks.selectList(new LambdaQueryWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getScheduleEnabled, true)
                .le(AgentSampleTaskDO::getNextFireAt, json.utc(at)).orderByAsc(AgentSampleTaskDO::getNextFireAt).last("LIMIT 20"));
    }

    /** 同一任务行锁 + 批次槽位唯一索引 + 触发审计，保证多个实例不重复生成相同批次。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void triggerScheduled(String taskId, Instant at) {
        // 调度触发按数据库中的原槽位幂等，允许多实例同时扫描而不重复创建同一槽位批次。
        var task = lockTask(taskId);
        if (!Boolean.TRUE.equals(task.getScheduleEnabled()) || task.getNextFireAt() == null) return;
        Instant planned = json.instant(task.getNextFireAt());
        if (planned.isAfter(at)) return;
        var schedule = schedule(task);
        requireProfile(spec(task));
        if (schedule.misfirePolicy() == ProductionSchedule.MisfirePolicy.FIRE_LATEST) {
            // 只补最新到期槽位，更早遗漏记为一个区间，避免停机后集中产生大量模型请求。
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
        // 从计划槽位而非实际执行时间推进，避免累计漂移；日期范围耗尽时自动关闭计划。
        Instant next = schedule.next(planned);
        var update = new LambdaUpdateWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getId, task.getId())
                .set(AgentSampleTaskDO::getNextFireAt, json.utc(next));
        if (next == null) update.set(AgentSampleTaskDO::getScheduleEnabled, false).setSql("version = version + 1");
        tasks.update(null, update);
    }

    /**
     * 在调用方事务与计划行锁内创建QUEUED批次；相同触发身份复用原批次。
     * key/hash是首次受理身份，trigger区分手动请求或精确槽位，kind只记录首次触发类型。
     * 固定期限从受理时刻起算，包含排队时间，Worker恢复不会延长它。
     */
    private AgentSampleRunDO queue(AgentSampleTaskDO task, String key, String hash, String trigger, String kind, Instant planned) {
        // queue只写入可恢复检查点；它不负责领取租约、提交线程或调用模型。
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
        row.setWorkflowVersion(SampleReflectionAgent.VERSION);
        row.setPhase(SampleReflectionAgent.Phase.GENERATE.name());
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

    /** 保存原UTC槽位的触发/遗漏事实；同槽位已有审计则保留，遗漏记录的runId可以为空。 */
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

    /** 查询当前主体的种子元数据，不返回原文。 */
    public FactoryResponses.Seed seed(String id) {
        // 列表和详情元数据不携带正文，避免普通导航接口扩大受限内容暴露面。
        var p = principals.currentPrincipal();
        return seedResponse(seed(p.tenantId(), p.actorId(), id));
    }

    /** 校验主体及原文摘要后单独返回种子正文；调用方应将正文作为不可信纯文本展示。 */
    public FactoryResponses.Content seedContent(String id) {
        // 正文读取再次校验摘要；返回后仍应作为不可信文本展示，不能直接拼接为HTML。
        var p = principals.currentPrincipal();
        var seed = seed(p.tenantId(), p.actorId(), id);
        return new FactoryResponses.Content(id, seed.getContent(), seed.getContentHash());
    }

    /** 查询计划冻结定义和当前调度控制状态；查询不刷新生成角色引用。 */
    public FactoryResponses.Task task(String id) {
        var p = principals.currentPrincipal();
        return taskResponse(authorizedTask(p.tenantId(), p.actorId(), id));
    }

    /** 返回当前主体最近100条计划，供导航选择；不是全量分页接口。 */
    public List<FactoryResponses.Task> tasks() {
        var p = principals.currentPrincipal();
        return tasks.selectList(new LambdaQueryWrapper<AgentSampleTaskDO>()
                .eq(AgentSampleTaskDO::getTenantId, p.tenantId()).eq(AgentSampleTaskDO::getActorId, p.actorId()).orderByDesc(AgentSampleTaskDO::getId).last("LIMIT 100")).stream().map(this::taskResponse).toList();
    }

    /** 返回当前主体最近100条种子元数据，正文仍须单独请求。 */
    public List<FactoryResponses.Seed> seeds() {
        var p = principals.currentPrincipal();
        return seeds.selectList(new LambdaQueryWrapper<AgentSampleSeedDO>()
                .eq(AgentSampleSeedDO::getTenantId, p.tenantId()).eq(AgentSampleSeedDO::getActorId, p.actorId()).orderByDesc(AgentSampleSeedDO::getId).last("LIMIT 100")).stream().map(this::seedResponse).toList();
    }

    /** 校验计划归属后返回最近100条调度审计，时间统一转为UTC时间点。 */
    public List<FactoryResponses.Fire> fires(String taskId) {
        var p = principals.currentPrincipal();
        authorizedTask(p.tenantId(), p.actorId(), taskId);
        return fires.selectList(new LambdaQueryWrapper<AgentSampleScheduleFireDO>().eq(AgentSampleScheduleFireDO::getTaskId, taskId).orderByDesc(AgentSampleScheduleFireDO::getId).last("LIMIT 100"))
                .stream().map(f -> new FactoryResponses.Fire(json.instant(f.getPlannedFor()), f.getKind(), f.getRunId(), f.getDetailsJson(), json.instant(f.getCreatedAt()))).toList();
    }

    /** 返回指定计划最近100个批次的当前快照，包括失败和停止的历史批次。 */
    public List<FactoryResponses.Run> runs(String taskId) {
        var p = principals.currentPrincipal();
        authorizedTask(p.tenantId(), p.actorId(), taskId);
        return runs.selectList(new LambdaQueryWrapper<AgentSampleRunDO>().eq(AgentSampleRunDO::getTaskId, taskId).orderByDesc(AgentSampleRunDO::getId).last("LIMIT 100")).stream().map(this::runResponse).toList();
    }

    /** 按计划、租户、操作者三者查询；不存在和越权统一报错，避免泄露他人资源是否存在。 */
    public AgentSampleTaskDO authorizedTask(String tenant, String actor, String id) {
        var row = tasks.selectOne(new LambdaQueryWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getTaskId, id).eq(AgentSampleTaskDO::getTenantId, tenant).eq(AgentSampleTaskDO::getActorId, actor));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return row;
    }

    /** 为HTTP与Worker共用的种子读取边界：检查归属，并核对原文SHA-256防止静默篡改。 */
    public AgentSampleSeedDO seed(String tenant, String actor, String id) {
        var row = seeds.selectOne(new LambdaQueryWrapper<AgentSampleSeedDO>().eq(AgentSampleSeedDO::getSeedId, id).eq(AgentSampleSeedDO::getTenantId, tenant).eq(AgentSampleSeedDO::getActorId, actor));
        if (row == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        if (!json.digest(row.getContent()).equals(row.getContentHash()))
            throw error(SampleFactoryErrorCode.PERSISTENCE_ERROR);
        return row;
    }

    /** 将计划中冻结的生成JSON恢复为领域契约，不从当前配置补写历史字段。 */
    public GenerationSpec spec(AgentSampleTaskDO task) {
        return json.read(task.getSpecJson(), GenerationSpec.class);
    }

    /** 读取单批预算上限；不是整个每日计划的累计预算。 */
    public ProductionBudget budget(AgentSampleTaskDO task) {
        return json.read(task.getBudgetJson(), ProductionBudget.class);
    }

    /** 读取冻结的每日调度定义；null表示该计划没有定时生产配置。 */
    public ProductionSchedule schedule(AgentSampleTaskDO task) {
        return task.getScheduleJson() == null ? null : json.read(task.getScheduleJson(), ProductionSchedule.class);
    }

    /** 转换运行快照；ordinal对外称completedSlots，表示已结束根槽位数而非合格样本数。 */
    public FactoryResponses.Run runResponse(AgentSampleRunDO r) {
        // 运行响应是当前数据库快照；创建批次接口返回的首次回执不会自动随Worker推进。
        return new FactoryResponses.Run(r.getRunId(), r.getTaskId(), r.getStatus(), r.getVersion(), r.getTriggerKind(),
                json.instant(r.getPlannedFor()), r.getOrdinal(), r.getRewriteRound(), r.getPendingOperationId(), json.read(r.getUsageJson(), ProductionBudget.Usage.class),
                Boolean.TRUE.equals(r.getStopRequested()), r.getErrorCode(), json.instant(r.getCreatedAt()), json.instant(r.getDeadlineAt()), json.instant(r.getFinishedAt()),
                r.getWorkflowVersion(), r.getPhase());
    }

    /** 查询资源内控制命令回执；没有回执返回null，同键异参报冲突，命中则返回首次结果。 */
    public FactoryResponses.Control controlReplay(String resource, String key, String hash) {
        var old = commands.selectOne(new LambdaQueryWrapper<AgentSampleCommandDO>().eq(AgentSampleCommandDO::getResourceId, resource).eq(AgentSampleCommandDO::getIdempotencyKey, key));
        if (old == null) return null;
        same(hash, old.getRequestHash());
        return json.read(old.getResultJson(), FactoryResponses.Control.class);
    }

    /** 在调用方业务事务内记录控制回执，使状态变更与幂等结果一起提交或回滚。 */
    public void saveControl(String resource, String key, String hash, FactoryResponses.Control result) {
        var row = new AgentSampleCommandDO();
        row.setResourceId(resource);
        row.setIdempotencyKey(key);
        row.setRequestHash(hash);
        row.setResultJson(json.write(result));
        commands.insert(row);
    }

    /** 事务内锁定计划行，串行化启停、调度与批次受理；HTTP调用者须先校验归属。 */
    private AgentSampleTaskDO lockTask(String id) {
        var t = tasks.selectOne(new LambdaQueryWrapper<AgentSampleTaskDO>().eq(AgentSampleTaskDO::getTaskId, id).last("LIMIT 1 FOR UPDATE"));
        if (t == null) throw error(SampleFactoryErrorCode.NOT_FOUND);
        return t;
    }

    /**
     * 新建或显式重置时冻结当前Actor版本，只替换模型指纹和提示词版本，专家约束保持原值。
     * 不用于查询或执行历史计划，避免部署升级后静默修改存量事实。
     */
    private GenerationSpec currentSpec(GenerationSpec source, String profileRef) {
        return new GenerationSpec(source.seedId(), source.seedVersion(), profileRef, SampleGenerationWorkflow.VERSION,
                source.riskId(), source.ruleSetVersion(), source.quantity(), source.mutations(), source.topicTerms(),
                source.minLength(), source.maxLength(), source.maxRewrites(), source.preconditions(), source.assertionDescription());
    }

    /** 校验生成与评审角色均可用且组合指纹与计划一致；不进行模型探活，也不替换旧快照。 */
    private void requireProfile(GenerationSpec spec) {
        // 配置不一致时宁可停止新增调用，也不把新模型混入旧计划的历史链路。
        var profile = models.profile();
        if (!profile.available() || !profile.profileRef().equals(spec.generatorProfileRef()))
            throw error(SampleFactoryErrorCode.GENERATOR_UNAVAILABLE);
    }

    /** 构造不可变版本1的种子索引，避免普通列表暴露正文。 */
    private FactoryResponses.Seed seedResponse(AgentSampleSeedDO s) {
        return new FactoryResponses.Seed(s.getSeedId(), 1, s.getName(), s.getContentHash(), s.getSourceRef(), json.instant(s.getCreatedAt()));
    }

    /** 组合冻结定义与可变控制状态，返回生产任务而非自动红队任务。 */
    private FactoryResponses.Task taskResponse(AgentSampleTaskDO t) {
        return new FactoryResponses.Task(t.getTaskId(), "SAMPLE_PRODUCTION", t.getName(), t.getActivityId(), t.getAuthorizationRef(),
                spec(t), budget(t), schedule(t), Boolean.TRUE.equals(t.getScheduleEnabled()), json.instant(t.getNextFireAt()), t.getVersion(), json.instant(t.getCreatedAt()));
    }

    /** 幂等键只允许重放相同请求，不能携带新参数覆盖首次事实。 */
    private void same(String expected, String actual) {
        if (!expected.equals(actual)) throw error(SampleFactoryErrorCode.IDEMPOTENCY_CONFLICT);
    }

    /** 生成带业务前缀的UUID标识；不是数据库自增主键或分页游标。 */
    private String id(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    /** 使用UTC且截断为微秒，匹配MySQL DATETIME(6)存储精度。 */
    private LocalDateTime now() {
        return json.utc(Instant.now());
    }

    /** 统一构造D05安全错误，不附带SQL、种子或模型原文。 */
    private ClientException error(SampleFactoryErrorCode code) {
        return new ClientException(code.message(), code);
    }

    /** 重置请求的幂等摘要同时绑定来源计划，避免同一个请求键被错误回放到另一计划。 */
    private record ResetTaskIdentity(String sourceTaskId, String idempotencyKey, String name) {
    }
}
