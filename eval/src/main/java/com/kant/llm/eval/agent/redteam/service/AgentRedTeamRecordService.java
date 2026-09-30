package com.kant.llm.eval.agent.redteam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.agent.contract.budget.BudgetPolicy;
import com.kant.agent.contract.budget.BudgetSnapshot;
import com.kant.agent.contract.probe.ProbeExecutionStatus;
import com.kant.agent.contract.redteam.AttackActionType;
import com.kant.agent.contract.redteam.AttackStopReason;
import com.kant.agent.contract.redteam.RedTeamActionRecord;
import com.kant.agent.contract.redteam.RedTeamAttempt;
import com.kant.agent.contract.redteam.RedTeamRun;
import com.kant.agent.contract.redteam.RedTeamRunStatus;
import com.kant.agent.contract.redteam.RedTeamTask;
import com.kant.agent.contract.redteam.RedTeamTaskStatus;
import com.kant.agent.contract.redteam.SuspectedFinding;
import com.kant.agent.port.redteam.RedTeamRunRecorder;
import com.kant.agent.port.redteam.RedTeamRunStarter;
import com.kant.agent.port.redteam.RedTeamTaskRepository;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamActionDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamAttemptDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamFindingDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamRunDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamTaskDO;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamActionMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamAttemptMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamFindingMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamRunMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 自动红队 D02 运行事实记录服务。
 *
 * <p>这里的写入都是短事务。它不包裹模型调用，也不把外部目标的等待时间
 * 放进数据库事务中。输入与响应正文仍由 D01 的 {@code agent_evidence} 表保存。</p>
 */
@Service
public class AgentRedTeamRecordService implements RedTeamTaskRepository, RedTeamRunRecorder, RedTeamRunStarter {

    private final AgentRedTeamTaskMapper taskMapper;
    private final AgentRedTeamRunMapper runMapper;
    private final AgentRedTeamAttemptMapper attemptMapper;
    private final AgentRedTeamActionMapper actionMapper;
    private final AgentRedTeamFindingMapper findingMapper;
    private final ObjectMapper objectMapper;

    public AgentRedTeamRecordService(AgentRedTeamTaskMapper taskMapper,
                                     AgentRedTeamRunMapper runMapper,
                                     AgentRedTeamAttemptMapper attemptMapper,
                                     AgentRedTeamActionMapper actionMapper,
                                     AgentRedTeamFindingMapper findingMapper,
                                     ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.attemptMapper = attemptMapper;
        this.actionMapper = actionMapper;
        this.findingMapper = findingMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(RedTeamTask task) {
        AgentRedTeamTaskDO entity = new AgentRedTeamTaskDO();
        entity.setTaskId(task.taskId());
        entity.setIdempotencyKey(task.idempotencyKey());
        entity.setTenantId(task.tenantId());
        entity.setActorId(task.actorId());
        entity.setActivityId(task.activityId());
        entity.setAuthorizationRef(task.authorizationRef());
        entity.setTargetType(task.target().targetType().name());
        entity.setTargetId(task.target().targetId());
        entity.setTargetSnapshotRef(task.targetSnapshotRef());
        entity.setRiskId(task.riskId());
        entity.setRuleSetVersion(task.ruleSetVersion());
        entity.setExecutionMode(task.executionMode().name());
        entity.setStrategyVersion(task.strategyVersion());
        entity.setSeedInput(task.seedInput());
        entity.setRiskIndicatorsJson(writeJson(task.riskIndicators()));
        entity.setBudgetPolicyJson(writeJson(task.budgetPolicy()));
        entity.setStatus(task.status().name());
        entity.setStopReason(enumName(task.stopReason()));
        entity.setStopMessage(task.stopMessage());
        entity.setCreatedAt(toLocalDateTime(task.createdAt()));
        entity.setStartedAt(toLocalDateTime(task.startedAt()));
        entity.setFinishedAt(toLocalDateTime(task.finishedAt()));
        entity.setVersion(0L);
        entity.setDeleted(false);
        taskMapper.insert(entity);
    }

    @Override
    public Optional<RedTeamTask> find(String tenantId, String actorId, String taskId) {
        return Optional.ofNullable(taskMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamTaskDO>()
                        .eq(AgentRedTeamTaskDO::getTenantId, tenantId)
                        .eq(AgentRedTeamTaskDO::getActorId, actorId)
                        .eq(AgentRedTeamTaskDO::getTaskId, taskId)
                        .last("LIMIT 1")))
                .map(this::toTask);
    }

    @Override
    public Optional<RedTeamTask> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return Optional.ofNullable(taskMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamTaskDO>()
                        .eq(AgentRedTeamTaskDO::getTenantId, tenantId)
                        .eq(AgentRedTeamTaskDO::getIdempotencyKey, idempotencyKey)
                        .last("LIMIT 1")))
                .map(this::toTask);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(String tenantId, String actorId, String taskId, RedTeamTask task) {
        AgentRedTeamTaskDO entity = findTaskEntity(tenantId, actorId, taskId)
                .orElseThrow(() -> new IllegalStateException("自动红队任务不存在，taskId=" + taskId));
        entity.setStatus(task.status().name());
        entity.setStopReason(enumName(task.stopReason()));
        entity.setStopMessage(task.stopMessage());
        entity.setStartedAt(toLocalDateTime(task.startedAt()));
        entity.setFinishedAt(toLocalDateTime(task.finishedAt()));
        entity.setVersion(nextVersion(entity.getVersion()));
        taskMapper.updateById(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createRun(RedTeamRun run) {
        AgentRedTeamRunDO entity = new AgentRedTeamRunDO();
        copyRun(run, entity);
        entity.setVersion(0L);
        entity.setDeleted(false);
        runMapper.insert(entity);
    }

    /**
     * 在一个短事务中条件抢占任务并创建活动运行。
     *
     * <p>条件更新只允许当前数据库状态仍等于调用方读取到的状态。若另一个
     * 请求已经先抢占任务，本次更新返回0，不会插入运行记录；若运行插入失败，
     * 同一事务会回滚任务抢占，避免留下只有任务RUNNING而没有运行事实的状态。</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryCreateAndClaim(RedTeamTask expectedTask,
                                     RedTeamTask runningTask,
                                     RedTeamRun runningRun) {
        int changed = taskMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamTaskDO>()
                .eq(AgentRedTeamTaskDO::getTenantId, expectedTask.tenantId())
                .eq(AgentRedTeamTaskDO::getActorId, expectedTask.actorId())
                .eq(AgentRedTeamTaskDO::getTaskId, expectedTask.taskId())
                .eq(AgentRedTeamTaskDO::getStatus, expectedTask.status().name())
                .set(AgentRedTeamTaskDO::getStatus, runningTask.status().name())
                .set(AgentRedTeamTaskDO::getStartedAt, toLocalDateTime(runningTask.startedAt()))
                // MyBatis-Plus 默认不会把 null 写回数据库，这里显式清掉上一次
                // 终态运行留下的停止信息和结束时间，避免RUNNING状态携带旧事实。
                .setSql("stop_reason = NULL, stop_message = NULL, finished_at = NULL, version = version + 1"));
        if (changed != 1) {
            return false;
        }

        AgentRedTeamRunDO entity = new AgentRedTeamRunDO();
        copyRun(runningRun, entity);
        entity.setVersion(0L);
        entity.setDeleted(false);
        runMapper.insert(entity);
        return true;
    }

    @Override
    public Optional<RedTeamRun> findRun(String tenantId, String actorId, String runId) {
        return Optional.ofNullable(findRunEntity(tenantId, actorId, runId).orElse(null))
                .map(this::toRun);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRun(RedTeamRun run) {
        // 使用定向更新，不回写stopRequested字段。停止请求由独立事务设置，
        // 若这里先读后写整个实体，可能把并发到达的停止意图覆盖回false。
        int changed = runMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, run.tenantId())
                .eq(AgentRedTeamRunDO::getActorId, run.actorId())
                .eq(AgentRedTeamRunDO::getRunId, run.runId())
                .set(AgentRedTeamRunDO::getStatus, run.status().name())
                .set(AgentRedTeamRunDO::getTargetCalls, run.budget().targetCalls())
                .set(AgentRedTeamRunDO::getAgentCalls, run.budget().agentCalls())
                .set(AgentRedTeamRunDO::getAttempts, run.budget().attempts())
                .set(AgentRedTeamRunDO::getTokens, run.budget().tokens())
                .set(AgentRedTeamRunDO::getReservedTokens, run.budget().reservedTokens())
                .set(AgentRedTeamRunDO::getActionCount, (long) run.actionCount())
                .set(AgentRedTeamRunDO::getCurrentAttemptId, run.currentAttemptId())
                .set(AgentRedTeamRunDO::getStopReason, enumName(run.stopReason()))
                .set(AgentRedTeamRunDO::getStopMessage, run.stopMessage())
                .set(AgentRedTeamRunDO::getStartedAt, toLocalDateTime(run.startedAt()))
                .set(AgentRedTeamRunDO::getFinishedAt, toLocalDateTime(run.finishedAt()))
                .setSql("version = version + 1"));
        if (changed != 1) {
            throw new IllegalStateException("自动红队运行不存在或状态已变化，runId=" + run.runId());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAttempt(RedTeamAttempt attempt) {
        AgentRedTeamAttemptDO entity = attemptMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamAttemptDO>()
                .eq(AgentRedTeamAttemptDO::getAttemptId, attempt.attemptId())
                .last("LIMIT 1"));
        if (entity == null) {
            entity = new AgentRedTeamAttemptDO();
            entity.setAttemptId(attempt.attemptId());
            entity.setTaskId(attempt.taskId());
            entity.setRunId(attempt.runId());
            entity.setSequence(attempt.sequence());
            entity.setRiskId(attempt.riskId());
            entity.setStrategyVersion(attempt.strategyVersion());
            entity.setStartedAt(toLocalDateTime(attempt.startedAt()));
            entity.setDeleted(false);
        }
        entity.setStatus(attempt.status().name());
        entity.setProbeCount(attempt.probeCount());
        entity.setStopReason(enumName(attempt.stopReason()));
        entity.setStopMessage(attempt.stopMessage());
        entity.setFinishedAt(toLocalDateTime(attempt.finishedAt()));
        if (entity.getId() == null) {
            attemptMapper.insert(entity);
        } else {
            attemptMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAction(RedTeamActionRecord action) {
        AgentRedTeamActionDO entity = actionMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamActionDO>()
                .eq(AgentRedTeamActionDO::getActionId, action.actionId())
                .last("LIMIT 1"));
        if (entity == null) {
            entity = new AgentRedTeamActionDO();
            entity.setActionId(action.actionId());
            entity.setCreatedAt(toLocalDateTime(action.createdAt()));
            entity.setDeleted(false);
        }
        entity.setActionId(action.actionId());
        entity.setTaskId(action.taskId());
        entity.setRunId(action.runId());
        entity.setAttemptId(action.attemptId());
        entity.setSequence(action.sequence());
        entity.setActionType(action.actionType().name());
        entity.setStrategyVersion(action.strategyVersion());
        entity.setInputHash(action.inputHash());
        entity.setReasonSummary(action.reasonSummary());
        entity.setObservationRefsJson(writeJson(action.observationRefs()));
        entity.setSuggestedStopReason(enumName(action.suggestedStopReason()));
        entity.setActualStopReason(enumName(action.actualStopReason()));
        entity.setInputEvidenceRef(action.inputEvidenceRef());
        entity.setProbeId(action.probeId());
        entity.setExecutionId(action.executionId());
        entity.setExecutionStatus(enumName(action.executionStatus()));
        entity.setResponseEvidenceRef(action.responseEvidenceRef());
        entity.setErrorCode(action.errorCode());
        entity.setRiskObserved(action.riskObserved());
        entity.setActionStatus(action.actionStatus());
        if (entity.getId() == null) {
            actionMapper.insert(entity);
        } else {
            actionMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFinding(SuspectedFinding finding) {
        // 发现是追加式事实。恢复或重复消息再次到达时复用同一个 findingId，
        // 不重复插入第二条记录，也不覆盖首次发现的证据快照。
        AgentRedTeamFindingDO existing = findingMapper.selectOne(
                new LambdaQueryWrapper<AgentRedTeamFindingDO>()
                        .eq(AgentRedTeamFindingDO::getFindingId, finding.findingId())
                        .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        AgentRedTeamFindingDO entity = new AgentRedTeamFindingDO();
        entity.setFindingId(finding.findingId());
        entity.setTaskId(finding.taskId());
        entity.setRunId(finding.runId());
        entity.setAttemptId(finding.attemptId());
        entity.setActionId(finding.actionId());
        entity.setRiskId(finding.riskId());
        entity.setEvidenceRefsJson(writeJson(finding.evidenceRefs()));
        entity.setReason(finding.reason());
        entity.setVerificationStatus(finding.verificationStatus());
        entity.setCreatedAt(toLocalDateTime(finding.createdAt()));
        entity.setDeleted(false);
        findingMapper.insert(entity);
    }

    @Override
    public List<RedTeamActionRecord> listActions(String tenantId, String actorId, String runId) {
        // 先用运行表校验身份，再按全局唯一的runId读取动作明细。
        if (findRunEntity(tenantId, actorId, runId).isEmpty()) {
            return List.of();
        }
        return actionMapper.selectList(new LambdaQueryWrapper<AgentRedTeamActionDO>()
                        .eq(AgentRedTeamActionDO::getRunId, runId)
                        .orderByAsc(AgentRedTeamActionDO::getSequence))
                .stream()
                .map(this::toAction)
                .toList();
    }

    @Override
    public List<RedTeamAttempt> listAttempts(String tenantId, String actorId, String runId) {
        // 先用运行表校验租户和操作者，再读取尝试；不能只凭 runId
        // 暴露攻击尝试事实。runId 在数据库中全局唯一，读取明细时仍保留
        // 运行边界校验，便于未来改成分区或非全局 ID。
        if (findRunEntity(tenantId, actorId, runId).isEmpty()) {
            return List.of();
        }
        return attemptMapper.selectList(new LambdaQueryWrapper<AgentRedTeamAttemptDO>()
                        .eq(AgentRedTeamAttemptDO::getRunId, runId)
                        .orderByAsc(AgentRedTeamAttemptDO::getSequence))
                .stream()
                .map(this::toAttempt)
                .toList();
    }

    @Override
    public List<SuspectedFinding> listFindings(String tenantId, String actorId, String runId) {
        // 先用运行表校验身份，再读取属于该运行的疑似发现。
        if (findRunEntity(tenantId, actorId, runId).isEmpty()) {
            return List.of();
        }
        return findingMapper.selectList(new LambdaQueryWrapper<AgentRedTeamFindingDO>()
                        .eq(AgentRedTeamFindingDO::getRunId, runId)
                        .orderByAsc(AgentRedTeamFindingDO::getCreatedAt))
                .stream()
                .map(this::toFinding)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestStop(String tenantId, String actorId, String runId) {
        runMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, tenantId)
                .eq(AgentRedTeamRunDO::getActorId, actorId)
                .eq(AgentRedTeamRunDO::getRunId, runId)
                .in(AgentRedTeamRunDO::getStatus,
                        RedTeamRunStatus.QUEUED.name(), RedTeamRunStatus.RUNNING.name())
                .set(AgentRedTeamRunDO::getStopRequested, true)
                .setSql("version = version + 1"));
    }

    @Override
    public boolean isStopRequested(String tenantId, String actorId, String runId) {
        AgentRedTeamRunDO entity = findRunEntity(tenantId, actorId, runId).orElse(null);
        return entity != null && Boolean.TRUE.equals(entity.getStopRequested());
    }

    private Optional<AgentRedTeamTaskDO> findTaskEntity(String tenantId, String actorId, String taskId) {
        return Optional.ofNullable(taskMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamTaskDO>()
                .eq(AgentRedTeamTaskDO::getTenantId, tenantId)
                .eq(AgentRedTeamTaskDO::getActorId, actorId)
                .eq(AgentRedTeamTaskDO::getTaskId, taskId)
                .last("LIMIT 1")));
    }

    private Optional<AgentRedTeamRunDO> findRunEntity(String tenantId, String actorId, String runId) {
        return Optional.ofNullable(runMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, tenantId)
                .eq(AgentRedTeamRunDO::getActorId, actorId)
                .eq(AgentRedTeamRunDO::getRunId, runId)
                .last("LIMIT 1")));
    }

    private RedTeamTask toTask(AgentRedTeamTaskDO entity) {
        return new RedTeamTask(
                entity.getTaskId(),
                entity.getIdempotencyKey(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getActivityId(),
                entity.getAuthorizationRef(),
                new com.kant.agent.contract.target.TargetReference(
                        com.kant.agent.contract.target.TargetType.valueOf(entity.getTargetType()),
                        entity.getTargetId()),
                entity.getTargetSnapshotRef(),
                entity.getRiskId(),
                entity.getRuleSetVersion(),
                com.kant.agent.contract.probe.ExecutionMode.valueOf(entity.getExecutionMode()),
                entity.getStrategyVersion(),
                entity.getSeedInput(),
                readList(entity.getRiskIndicatorsJson()),
                readBudget(entity.getBudgetPolicyJson()),
                RedTeamTaskStatus.valueOf(entity.getStatus()),
                enumValue(AttackStopReason.class, entity.getStopReason()),
                entity.getStopMessage(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getStartedAt()),
                toInstant(entity.getFinishedAt()));
    }

    private RedTeamRun toRun(AgentRedTeamRunDO entity) {
        return new RedTeamRun(
                entity.getRunId(),
                entity.getTaskId(),
                entity.getTenantId(),
                entity.getActorId(),
                RedTeamRunStatus.valueOf(entity.getStatus()),
                new BudgetSnapshot(defaultLong(entity.getTargetCalls()),
                        defaultLong(entity.getAgentCalls()),
                        defaultLong(entity.getAttempts()),
                        defaultLong(entity.getTokens()),
                        defaultLong(entity.getReservedTokens())),
                Math.toIntExact(defaultLong(entity.getActionCount())),
                entity.getCurrentAttemptId(),
                enumValue(AttackStopReason.class, entity.getStopReason()),
                entity.getStopMessage(),
                Boolean.TRUE.equals(entity.getStopRequested()),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getStartedAt()),
                toInstant(entity.getFinishedAt()));
    }

    private RedTeamAttempt toAttempt(AgentRedTeamAttemptDO entity) {
        return new RedTeamAttempt(
                entity.getAttemptId(),
                entity.getTaskId(),
                entity.getRunId(),
                defaultInt(entity.getSequence()),
                entity.getRiskId(),
                entity.getStrategyVersion(),
                RedTeamRunStatus.valueOf(entity.getStatus()),
                defaultInt(entity.getProbeCount()),
                enumValue(AttackStopReason.class, entity.getStopReason()),
                entity.getStopMessage(),
                toInstant(entity.getStartedAt()),
                toInstant(entity.getFinishedAt()));
    }

    private RedTeamActionRecord toAction(AgentRedTeamActionDO entity) {
        return new RedTeamActionRecord(
                entity.getActionId(),
                entity.getTaskId(),
                entity.getRunId(),
                entity.getAttemptId(),
                entity.getSequence(),
                AttackActionType.valueOf(entity.getActionType()),
                entity.getStrategyVersion(),
                entity.getInputHash(),
                entity.getReasonSummary(),
                readList(entity.getObservationRefsJson()),
                enumValue(AttackStopReason.class, entity.getSuggestedStopReason()),
                enumValue(AttackStopReason.class, entity.getActualStopReason()),
                entity.getInputEvidenceRef(),
                entity.getProbeId(),
                entity.getExecutionId(),
                enumValue(ProbeExecutionStatus.class, entity.getExecutionStatus()),
                entity.getResponseEvidenceRef(),
                entity.getErrorCode(),
                Boolean.TRUE.equals(entity.getRiskObserved()),
                entity.getActionStatus(),
                toInstant(entity.getCreatedAt()));
    }

    private SuspectedFinding toFinding(AgentRedTeamFindingDO entity) {
        return new SuspectedFinding(
                entity.getFindingId(),
                entity.getTaskId(),
                entity.getRunId(),
                entity.getAttemptId(),
                entity.getActionId(),
                entity.getRiskId(),
                readList(entity.getEvidenceRefsJson()),
                entity.getReason(),
                entity.getVerificationStatus(),
                toInstant(entity.getCreatedAt()));
    }

    private void copyRun(RedTeamRun run, AgentRedTeamRunDO entity) {
        entity.setRunId(run.runId());
        entity.setTaskId(run.taskId());
        entity.setTenantId(run.tenantId());
        entity.setActorId(run.actorId());
        entity.setStatus(run.status().name());
        entity.setTargetCalls(run.budget().targetCalls());
        entity.setAgentCalls(run.budget().agentCalls());
        entity.setAttempts(run.budget().attempts());
        entity.setTokens(run.budget().tokens());
        entity.setReservedTokens(run.budget().reservedTokens());
        entity.setActionCount((long) run.actionCount());
        entity.setCurrentAttemptId(run.currentAttemptId());
        entity.setStopReason(enumName(run.stopReason()));
        entity.setStopMessage(run.stopMessage());
        entity.setStopRequested(run.stopRequested());
        entity.setCreatedAt(toLocalDateTime(run.createdAt()));
        entity.setStartedAt(toLocalDateTime(run.startedAt()));
        entity.setFinishedAt(toLocalDateTime(run.finishedAt()));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("自动红队配置序列化失败", ex);
        }
    }

    /** 读取 D02 中统一使用的字符串引用列表。 */
    private List<String> readList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("自动红队列表字段解析失败", ex);
        }
    }

    private BudgetPolicy readBudget(String value) {
        try {
            return objectMapper.readValue(value, BudgetPolicy.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("自动红队预算配置解析失败", ex);
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null || value.isBlank() ? null : Enum.valueOf(type, value);
    }

    private Long nextVersion(Long version) {
        return version == null ? 1L : version + 1L;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
