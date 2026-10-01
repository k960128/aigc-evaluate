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
import com.kant.agent.contract.redteam.AttackSession;
import com.kant.agent.contract.redteam.AttackSessionStatus;
import com.kant.agent.contract.redteam.AttackStopReason;
import com.kant.agent.contract.redteam.RedTeamActionRecord;
import com.kant.agent.contract.redteam.RedTeamAttempt;
import com.kant.agent.contract.redteam.RedTeamRun;
import com.kant.agent.contract.redteam.RedTeamRunStatus;
import com.kant.agent.contract.redteam.RedTeamTask;
import com.kant.agent.contract.redteam.RedTeamTaskStatus;
import com.kant.agent.contract.redteam.SessionCheckpoint;
import com.kant.agent.contract.redteam.SuspectedFinding;
import com.kant.agent.port.redteam.RedTeamRunRecorder;
import com.kant.agent.port.redteam.RedTeamRunStarter;
import com.kant.agent.port.redteam.RedTeamTaskRepository;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamActionDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamAttemptDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamFindingDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamRunDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamSessionCheckpointDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamSessionDO;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamTaskDO;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamActionMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamAttemptMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamFindingMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamRunMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamSessionCheckpointMapper;
import com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamSessionMapper;
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
    private final AgentRedTeamSessionMapper sessionMapper;
    private final AgentRedTeamSessionCheckpointMapper checkpointMapper;
    private final ObjectMapper objectMapper;
    private final com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamOperationMapper operationMapper;
    private final com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamEventMapper eventMapper;
    private final com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamCommandMapper commandMapper;

    public AgentRedTeamRecordService(AgentRedTeamTaskMapper taskMapper,
                                     AgentRedTeamRunMapper runMapper,
                                     AgentRedTeamAttemptMapper attemptMapper,
                                     AgentRedTeamActionMapper actionMapper,
                                     AgentRedTeamFindingMapper findingMapper,
                                     AgentRedTeamSessionMapper sessionMapper,
                                     AgentRedTeamSessionCheckpointMapper checkpointMapper,
                                     ObjectMapper objectMapper,
                                     com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamOperationMapper operationMapper,
                                     com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamEventMapper eventMapper,
                                     com.kant.llm.eval.agent.redteam.dao.mapper.AgentRedTeamCommandMapper commandMapper) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.attemptMapper = attemptMapper;
        this.actionMapper = actionMapper;
        this.findingMapper = findingMapper;
        this.sessionMapper = sessionMapper;
        this.checkpointMapper = checkpointMapper;
        this.objectMapper = objectMapper;
        this.operationMapper = operationMapper;
        this.eventMapper = eventMapper;
        this.commandMapper = commandMapper;
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
        appendEvent(runningRun.runId(), "RUN_ACCEPTED", runningRun.status().name(), 0, "运行已受理");
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
        AgentRedTeamRunDO current = findRunEntity(run.tenantId(), run.actorId(), run.runId())
                .orElseThrow(() -> new IllegalStateException("自动红队运行不存在，runId=" + run.runId()));
        String effectiveStatus = run.status().name();
        // 暂停命令是独立并发写入的控制事实。编排器在同一动作边界保存
        // RUNNING 快照时，不能把 PAUSING 回写成 RUNNING。
        if (RedTeamRunStatus.PAUSING.name().equals(current.getStatus())
                && RedTeamRunStatus.RUNNING == run.status()) {
            effectiveStatus = RedTeamRunStatus.PAUSING.name();
        }
        int changed = runMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, run.tenantId())
                .eq(AgentRedTeamRunDO::getActorId, run.actorId())
                .eq(AgentRedTeamRunDO::getRunId, run.runId())
                .set(AgentRedTeamRunDO::getStatus, effectiveStatus)
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
        int changed = runMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, tenantId)
                .eq(AgentRedTeamRunDO::getActorId, actorId)
                .eq(AgentRedTeamRunDO::getRunId, runId)
                .in(AgentRedTeamRunDO::getStatus,
                        RedTeamRunStatus.QUEUED.name(),
                        RedTeamRunStatus.RUNNING.name(),
                        RedTeamRunStatus.PAUSING.name(),
                        RedTeamRunStatus.PAUSED.name(),
                        RedTeamRunStatus.BLOCKED.name())
                .set(AgentRedTeamRunDO::getStopRequested, true)
                .setSql("version = version + 1"));
        sessionMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamSessionDO>()
                .eq(AgentRedTeamSessionDO::getTenantId, tenantId)
                .eq(AgentRedTeamSessionDO::getActorId, actorId)
                .eq(AgentRedTeamSessionDO::getRunId, runId)
                .in(AgentRedTeamSessionDO::getStatus,
                        AttackSessionStatus.OPENING.name(),
                        AttackSessionStatus.ACTIVE.name(),
                        AttackSessionStatus.PAUSING.name(),
                        AttackSessionStatus.PAUSED.name(),
                        AttackSessionStatus.BLOCKED.name())
                .set(AgentRedTeamSessionDO::getStopRequested, true)
                .setSql("updated_at = CURRENT_TIMESTAMP(3)"));
        if (changed == 1) commandEvent(tenantId,actorId,runId,"STOP_REQUESTED","停止意图已保存，已发调用仍可能返回");
    }

    @Override
    public boolean isStopRequested(String tenantId, String actorId, String runId) {
        AgentRedTeamRunDO entity = findRunEntity(tenantId, actorId, runId).orElse(null);
        return entity != null && Boolean.TRUE.equals(entity.getStopRequested());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createSession(AttackSession session) {
        AgentRedTeamSessionDO entity = new AgentRedTeamSessionDO();
        copySession(session, entity);
        entity.setDeleted(false);
        sessionMapper.insert(entity);
    }

    @Override
    public Optional<AttackSession> findSession(String tenantId, String actorId, String sessionId) {
        return Optional.ofNullable(sessionMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamSessionDO>()
                        .eq(AgentRedTeamSessionDO::getTenantId, tenantId)
                        .eq(AgentRedTeamSessionDO::getActorId, actorId)
                        .eq(AgentRedTeamSessionDO::getSessionId, sessionId)
                        .last("LIMIT 1")))
                .map(this::toSession);
    }

    @Override
    public Optional<AttackSession> findSessionByRun(String tenantId, String actorId, String runId) {
        return Optional.ofNullable(sessionMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamSessionDO>()
                        .eq(AgentRedTeamSessionDO::getTenantId, tenantId)
                        .eq(AgentRedTeamSessionDO::getActorId, actorId)
                        .eq(AgentRedTeamSessionDO::getRunId, runId)
                        .orderByDesc(AgentRedTeamSessionDO::getCreatedAt)
                        .last("LIMIT 1")))
                .map(this::toSession);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSession(AttackSession session) {
        AgentRedTeamSessionDO entity = sessionMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamSessionDO>()
                .eq(AgentRedTeamSessionDO::getTenantId, session.tenantId())
                .eq(AgentRedTeamSessionDO::getActorId, session.actorId())
                .eq(AgentRedTeamSessionDO::getSessionId, session.sessionId())
                .last("LIMIT 1"));
        if (entity == null) {
            throw new IllegalStateException("目标会话不存在，sessionId=" + session.sessionId());
        }
        // pauseRequested 和 stopRequested 是用户命令字段，必须由专用命令方法
        // 修改。编排器拿着旧快照写入下一检查点时不能把并发到达的命令覆盖掉。
        // 定向写状态和检查点，不回写命令标记，并显式允许清空旧错误及结束时间。
        sessionMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamSessionDO>()
                .eq(AgentRedTeamSessionDO::getId, entity.getId())
                .set(AgentRedTeamSessionDO::getStatus, session.status().name())
                .set(AgentRedTeamSessionDO::getTurnCount, session.turnCount())
                .set(AgentRedTeamSessionDO::getNextSequence, session.nextSequence())
                .set(AgentRedTeamSessionDO::getLastProbeId, session.lastProbeId())
                .set(AgentRedTeamSessionDO::getLastExecutionId, session.lastExecutionId())
                .set(AgentRedTeamSessionDO::getCheckpointVersion, session.checkpointVersion())
                .set(AgentRedTeamSessionDO::getUpdatedAt, toLocalDateTime(session.updatedAt()))
                .set(AgentRedTeamSessionDO::getClosedAt, toLocalDateTime(session.closedAt()))
                .set(AgentRedTeamSessionDO::getErrorCode, session.errorCode())
                .set(AgentRedTeamSessionDO::getErrorMessage, session.errorMessage()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCheckpoint(SessionCheckpoint checkpoint) {
        AgentRedTeamSessionCheckpointDO entity = new AgentRedTeamSessionCheckpointDO();
        entity.setCheckpointId(checkpoint.checkpointId());
        entity.setSessionId(checkpoint.sessionId());
        entity.setTaskId(checkpoint.taskId());
        entity.setRunId(checkpoint.runId());
        entity.setAttemptId(checkpoint.attemptId());
        entity.setCheckpointVersion(checkpoint.version());
        entity.setTurnCount(checkpoint.turnCount());
        entity.setNextSequence(checkpoint.nextSequence());
        entity.setLastProbeId(checkpoint.lastProbeId());
        entity.setLastExecutionId(checkpoint.lastExecutionId());
        entity.setHistoryHash(checkpoint.historyHash());
        entity.setStatus(checkpoint.status().name());
        entity.setCreatedAt(toLocalDateTime(checkpoint.createdAt()));
        entity.setDeleted(false);
        checkpointMapper.insert(entity);
    }

    @Override
    public Optional<SessionCheckpoint> findLatestCheckpoint(
            String tenantId,
            String actorId,
            String sessionId) {
        Optional<AttackSession> session = findSession(tenantId, actorId, sessionId);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(checkpointMapper.selectOne(
                        new LambdaQueryWrapper<AgentRedTeamSessionCheckpointDO>()
                                .eq(AgentRedTeamSessionCheckpointDO::getSessionId, sessionId)
                                .orderByDesc(AgentRedTeamSessionCheckpointDO::getCheckpointVersion)
                                .last("LIMIT 1")))
                .map(this::toCheckpoint);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestPause(String tenantId, String actorId, String runId) {
        int runChanged = runMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, tenantId)
                .eq(AgentRedTeamRunDO::getActorId, actorId)
                .eq(AgentRedTeamRunDO::getRunId, runId)
                .in(AgentRedTeamRunDO::getStatus,
                        RedTeamRunStatus.QUEUED.name(), RedTeamRunStatus.RUNNING.name())
                .eq(AgentRedTeamRunDO::getStopRequested, false)
                .set(AgentRedTeamRunDO::getStatus, RedTeamRunStatus.PAUSING.name())
                .setSql("version = version + 1"));
        if (runChanged == 0 && !findRunEntity(tenantId, actorId, runId)
                .map(run -> run.getStatus().equals(RedTeamRunStatus.PAUSING.name()))
                .orElse(false)) {
            throw new IllegalStateException("运行不存在或当前状态不能暂停，runId=" + runId);
        }
        // QUEUED 阶段可能还没有创建目标会话，运行状态本身先保存暂停意图；
        // 会话创建后由 Worker 在动作边界读取该意图并落下 PAUSED 检查点。
        sessionMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamSessionDO>()
                .eq(AgentRedTeamSessionDO::getTenantId, tenantId)
                .eq(AgentRedTeamSessionDO::getActorId, actorId)
                .eq(AgentRedTeamSessionDO::getRunId, runId)
                .in(AgentRedTeamSessionDO::getStatus,
                        AttackSessionStatus.OPENING.name(), AttackSessionStatus.ACTIVE.name())
                .set(AgentRedTeamSessionDO::getStatus, AttackSessionStatus.PAUSING.name())
                .set(AgentRedTeamSessionDO::getPauseRequested, true)
                .setSql("updated_at = CURRENT_TIMESTAMP(3)"));
        if (runChanged == 1) commandEvent(tenantId,actorId,runId,"PAUSE_REQUESTED","等待当前动作结束并保存检查点");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestResume(String tenantId, String actorId, String runId) {
        int runChanged = runMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, tenantId)
                .eq(AgentRedTeamRunDO::getActorId, actorId)
                .eq(AgentRedTeamRunDO::getRunId, runId)
                .eq(AgentRedTeamRunDO::getStatus, RedTeamRunStatus.PAUSED.name())
                .eq(AgentRedTeamRunDO::getStopRequested, false)
                .set(AgentRedTeamRunDO::getStatus, RedTeamRunStatus.RUNNING.name())
                .setSql("stop_reason = NULL, stop_message = NULL, finished_at = NULL, version = version + 1"));
        commandEvent(tenantId,actorId,runId,"RESUME_ACCEPTED","原会话恢复已受理，截止时间保持不变");
        int sessionChanged = sessionMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamSessionDO>()
                .eq(AgentRedTeamSessionDO::getTenantId, tenantId)
                .eq(AgentRedTeamSessionDO::getActorId, actorId)
                .eq(AgentRedTeamSessionDO::getRunId, runId)
                .eq(AgentRedTeamSessionDO::getStatus, AttackSessionStatus.PAUSED.name())
                .eq(AgentRedTeamSessionDO::getStopRequested, false)
                .set(AgentRedTeamSessionDO::getStatus, AttackSessionStatus.ACTIVE.name())
                .set(AgentRedTeamSessionDO::getPauseRequested, false)
                .setSql("error_code = NULL, error_message = NULL, closed_at = NULL, updated_at = CURRENT_TIMESTAMP(3)"));
        if (runChanged != 1 || sessionChanged != 1) {
            throw new IllegalStateException("运行或会话状态已变化，无法恢复，runId=" + runId);
        }
        AgentRedTeamRunDO resumed = findRunEntity(tenantId, actorId, runId).orElseThrow();
        taskMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamTaskDO>()
                .eq(AgentRedTeamTaskDO::getTaskId, resumed.getTaskId())
                .set(AgentRedTeamTaskDO::getStatus, RedTeamTaskStatus.RUNNING.name())
                .setSql("stop_reason = NULL, stop_message = NULL, finished_at = NULL, version = version + 1"));
    }

    @Override
    public boolean isPauseRequested(String tenantId, String actorId, String runId) {
        AgentRedTeamRunDO run = findRunEntity(tenantId, actorId, runId).orElse(null);
        if (run != null && RedTeamRunStatus.PAUSING.name().equals(run.getStatus())) {
            return true;
        }
        AgentRedTeamSessionDO entity = sessionMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamSessionDO>()
                .eq(AgentRedTeamSessionDO::getTenantId, tenantId)
                .eq(AgentRedTeamSessionDO::getActorId, actorId)
                .eq(AgentRedTeamSessionDO::getRunId, runId)
                .last("LIMIT 1"));
        return entity != null && Boolean.TRUE.equals(entity.getPauseRequested());
    }

    @Override
    public List<RedTeamRun> listRecoverableRuns() {
        return runMapper.selectList(new LambdaQueryWrapper<AgentRedTeamRunDO>()
                        .in(AgentRedTeamRunDO::getStatus,
                                RedTeamRunStatus.QUEUED.name(),
                                RedTeamRunStatus.RUNNING.name(),
                                RedTeamRunStatus.PAUSING.name(), RedTeamRunStatus.PAUSED.name(), RedTeamRunStatus.BLOCKED.name())
                        .and(w -> w.isNull(AgentRedTeamRunDO::getWorkerLeaseUntil)
                                .or().lt(AgentRedTeamRunDO::getWorkerLeaseUntil, toLocalDateTime(Instant.now())))
                        .orderByAsc(AgentRedTeamRunDO::getCreatedAt))
                .stream()
                .map(this::toRun)
                .filter(run -> find(run.tenantId(), run.actorId(), run.taskId())
                        .map(task -> task.executionMode() == com.kant.agent.contract.probe.ExecutionMode.ADAPTIVE_MULTI_TURN
                                && (run.stopRequested() || run.status()!=RedTeamRunStatus.BLOCKED
                                && (run.status()!=RedTeamRunStatus.PAUSED
                                || !run.startedAt().plusSeconds(task.budgetPolicy().maxDurationSeconds()).isAfter(Instant.now()))))
                        .orElse(false))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaimWorker(RedTeamRun run, String workerId, Instant leaseUntil) {
        LocalDateTime now = toLocalDateTime(Instant.now());
        int changed = runMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, run.tenantId())
                .eq(AgentRedTeamRunDO::getActorId, run.actorId())
                .eq(AgentRedTeamRunDO::getRunId, run.runId())
                .in(AgentRedTeamRunDO::getStatus,
                        RedTeamRunStatus.QUEUED.name(),
                        RedTeamRunStatus.RUNNING.name(),
                        RedTeamRunStatus.PAUSING.name(), RedTeamRunStatus.PAUSED.name(), RedTeamRunStatus.BLOCKED.name())
                .and(wrapper -> wrapper.isNull(AgentRedTeamRunDO::getWorkerLeaseUntil)
                        .or()
                        .lt(AgentRedTeamRunDO::getWorkerLeaseUntil, now))
                .set(AgentRedTeamRunDO::getWorkerId, workerId)
                .set(AgentRedTeamRunDO::getWorkerLeaseUntil, toLocalDateTime(leaseUntil)));
        return changed == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewWorkerLease(RedTeamRun run, String workerId, Instant leaseUntil) {
        int changed = runMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, run.tenantId())
                .eq(AgentRedTeamRunDO::getActorId, run.actorId())
                .eq(AgentRedTeamRunDO::getRunId, run.runId())
                .eq(AgentRedTeamRunDO::getWorkerId, workerId)
                .gt(AgentRedTeamRunDO::getWorkerLeaseUntil, toLocalDateTime(Instant.now()))
                .set(AgentRedTeamRunDO::getWorkerLeaseUntil, toLocalDateTime(leaseUntil)));
        return changed == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseWorkerLease(RedTeamRun run, String workerId) {
        runMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, run.tenantId())
                .eq(AgentRedTeamRunDO::getActorId, run.actorId())
                .eq(AgentRedTeamRunDO::getRunId, run.runId())
                .eq(AgentRedTeamRunDO::getWorkerId, workerId)
                .set(AgentRedTeamRunDO::getWorkerId, null)
                .set(AgentRedTeamRunDO::getWorkerLeaseUntil, null));
    }

    /**
     * 租约栅栏与运行行锁保护整个检查点事务。以下同类方法是内部直接调用，
     * 不经过Spring代理，因此参与本事务，而不是各自再开启REQUIRES_NEW。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProgress(String workerId, RedTeamRun run, AttackSession session,
            SessionCheckpoint checkpoint, RedTeamAttempt attempt, RedTeamActionRecord action,
            SuspectedFinding finding, com.kant.agent.contract.redteam.PendingProbe operation) {
        AgentRedTeamRunDO locked = lockWorker(run, workerId);
        updateRun(run);
        if (session != null) {
            if (findSession(session.tenantId(), session.actorId(), session.sessionId()).isEmpty())
                createSession(session);
            else updateSession(session);
        }
        if (checkpoint != null) saveCheckpoint(checkpoint);
        if (attempt != null) saveAttempt(attempt);
        if (action != null) saveAction(action);
        if (finding != null) saveFinding(finding);
        if (operation != null) {
            var entity = operationMapper.selectOne(new LambdaQueryWrapper<com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO>()
                    .eq(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO::getRunId, run.runId())
                    .eq(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO::getSequence, operation.sequence()));
            if (entity == null) {
                entity = new com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO();
                entity.setRunId(run.runId());
                entity.setSequence(operation.sequence());
                entity.setActionId(operation.actionId());
                entity.setActionJson(writeJson(operation.action()));
                entity.setCommandJson(writeJson(operation.command()));
                entity.setReservedTokens(operation.reservedTokens());
                entity.setStatus(operation.status());
                entity.setCreatedAt(toLocalDateTime(Instant.now()));
                operationMapper.insert(entity);
            } else {
                // 操作日志的正文与幂等键不可变，只推进状态，不覆盖首次生成的输入。
                entity.setStatus(operation.status());
                operationMapper.updateById(entity);
            }
        }
        RedTeamTaskStatus taskStatus = switch (run.status()) {
            case PAUSED -> RedTeamTaskStatus.PAUSED;
            case BLOCKED -> RedTeamTaskStatus.BLOCKED;
            case COMPLETED -> RedTeamTaskStatus.COMPLETED;
            case FAILED -> RedTeamTaskStatus.FAILED;
            case STOPPED -> RedTeamTaskStatus.STOPPED;
            default -> RedTeamTaskStatus.RUNNING;
        };
        taskMapper.update(null, new LambdaUpdateWrapper<AgentRedTeamTaskDO>()
                .eq(AgentRedTeamTaskDO::getTaskId, run.taskId())
                .eq(AgentRedTeamTaskDO::getTenantId, run.tenantId())
                .eq(AgentRedTeamTaskDO::getActorId, run.actorId())
                .set(AgentRedTeamTaskDO::getStatus, taskStatus.name())
                .set(AgentRedTeamTaskDO::getStopReason, enumName(run.stopReason()))
                .set(AgentRedTeamTaskDO::getStopMessage, run.stopMessage())
                .set(AgentRedTeamTaskDO::getFinishedAt, toLocalDateTime(run.finishedAt()))
                .setSql("version = version + 1"));
        // 暂停命令可能先于本次检查点到达，事件必须使用事务内最终数据库状态。
        AgentRedTeamRunDO persisted = findRunEntity(run.tenantId(),run.actorId(),run.runId()).orElseThrow();
        appendEvent(run.runId(), "RUN_PROGRESS", persisted.getStatus(),
                defaultLong(persisted.getVersion()), run.stopMessage() == null ? "运行检查点已更新" : run.stopMessage());
    }

    @Override
    public Optional<com.kant.agent.contract.redteam.PendingProbe> findPendingProbe(
            String tenantId, String actorId, String runId) {
        if (findRunEntity(tenantId, actorId, runId).isEmpty()) return Optional.empty();
        var entity = operationMapper.selectOne(new LambdaQueryWrapper<com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO>()
                .eq(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO::getRunId, runId)
                .ne(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO::getStatus, "SETTLED")
                .orderByAsc(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO::getSequence)
                .last("LIMIT 1"));
        if (entity == null) return Optional.empty();
        try {
            return Optional.of(new com.kant.agent.contract.redteam.PendingProbe(entity.getSequence(), entity.getActionId(),
                    objectMapper.readValue(entity.getActionJson(), com.kant.agent.contract.redteam.AttackAction.class),
                    objectMapper.readValue(entity.getCommandJson(), com.kant.agent.contract.probe.ProbeCommand.class),
                    entity.getReservedTokens(), entity.getStatus()));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("受限操作日志解析失败", ex); }
    }

    /** D01多轮受理前使用同一运行行锁校验执行线程租约和用户控制意图。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void requireWorkerForProbe(com.kant.agent.contract.probe.ProbeCommand command) {
        var lease = com.kant.llm.eval.agent.redteam.worker.RedTeamWorkerContext.current();
        if (lease == null || !lease.run().runId().equals(command.context().runId())
                || !lease.run().tenantId().equals(command.context().tenantId())
                || !lease.run().actorId().equals(command.context().actorId()))
            throw new com.kant.agent.service.redteam.WorkerLeaseLostException();
        AgentRedTeamRunDO locked = lockWorker(lease.run(), lease.workerId());
        if (Boolean.TRUE.equals(locked.getStopRequested())
                || RedTeamRunStatus.PAUSING.name().equals(locked.getStatus())
                || !RedTeamRunStatus.RUNNING.name().equals(locked.getStatus())
                || command.deadlineAt() == null || !command.deadlineAt().isAfter(Instant.now()))
            throw new IllegalStateException("运行已暂停、停止、过期或不允许追加调用");
        AttackSession session = findSession(command.context().tenantId(), command.context().actorId(), command.sessionRef()).orElseThrow();
        if (session.status() != AttackSessionStatus.ACTIVE || !session.runId().equals(command.context().runId())
                || !session.taskId().equals(command.context().taskId())
                || !session.attemptId().equals(command.context().attemptId())
                || !session.targetSnapshotRef().equals(command.expectedTargetSnapshotRef()))
            throw new IllegalStateException("目标会话身份或快照不匹配");
        var pending = findPendingProbe(command.context().tenantId(),command.context().actorId(),command.context().runId())
                .orElseThrow(() -> new IllegalStateException("缺少已持久化的调用意图"));
        if (!command.equals(pending.command()) || !"DISPATCHING".equals(pending.status()))
            throw new IllegalStateException("调用与已批准的固定操作不一致");
    }

    private AgentRedTeamRunDO lockWorker(RedTeamRun run, String workerId) {
        AgentRedTeamRunDO entity = runMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId, run.tenantId())
                .eq(AgentRedTeamRunDO::getActorId, run.actorId())
                .eq(AgentRedTeamRunDO::getRunId, run.runId())
                .last("LIMIT 1 FOR UPDATE"));
        if (entity == null || !java.util.Objects.equals(workerId, entity.getWorkerId())
                || entity.getWorkerLeaseUntil() == null
                || !entity.getWorkerLeaseUntil().isAfter(toLocalDateTime(Instant.now())))
            throw new com.kant.agent.service.redteam.WorkerLeaseLostException();
        return entity;
    }

    /** 只追加安全元数据，不记录操作正文。必须在状态更新的同一事务内调用。 */
    private void appendEvent(String runId, String type, String status, long version, String summary) {
        var entity = new com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamEventDO();
        entity.setRunId(runId); entity.setEventType(type); entity.setStatus(status);
        entity.setRunVersion(version); entity.setSummary(summary); entity.setCreatedAt(toLocalDateTime(Instant.now()));
        eventMapper.insert(entity);
    }

    /** 控制意图与事件同事务保存。调用者已通过更新或FOR UPDATE持有运行行锁。 */
    private void commandEvent(String tenant,String actor,String runId,String type,String summary) {
        AgentRedTeamRunDO run = findRunEntity(tenant,actor,runId).orElseThrow();
        appendEvent(runId,type,run.getStatus(),defaultLong(run.getVersion()),summary);
    }

    /**
     * 版本化命令受理：先校验身份，再锁运行行，先处理幂等重放后校验版本。
     * 同类控制方法直接调用参与本事务，命令、状态与事件不会各自提交。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public com.kant.llm.eval.agent.redteam.dto.resp.RedTeamCommandReceipt acceptCommand(String tenant,String actor,String runId,
            com.kant.llm.eval.agent.redteam.dto.req.RedTeamRunCommandRequest request, Runnable resumeValidator) {
        AgentRedTeamRunDO run = runMapper.selectOne(new LambdaQueryWrapper<AgentRedTeamRunDO>()
                .eq(AgentRedTeamRunDO::getTenantId,tenant).eq(AgentRedTeamRunDO::getActorId,actor)
                .eq(AgentRedTeamRunDO::getRunId,runId).last("LIMIT 1 FOR UPDATE"));
        if (run==null) throw new com.kant.framework.exception.ClientException("运行不存在或无权访问",
                com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode.RUN_NOT_FOUND);
        var existing = commandMapper.selectOne(new LambdaQueryWrapper<com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamCommandDO>()
                .eq(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamCommandDO::getRunId,runId)
                .eq(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamCommandDO::getIdempotencyKey,request.idempotencyKey()));
        if (existing!=null) {
            if (!existing.getActorId().equals(actor) || !existing.getType().equals(request.type().name())
                    || !existing.getExpectedVersion().equals(request.expectedVersion()))
                throw new com.kant.framework.exception.ClientException("同键命令内容不同",
                        com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode.IDEMPOTENCY_CONFLICT);
            return receipt(existing);
        }
        if (defaultLong(run.getVersion())!=request.expectedVersion())
            throw new com.kant.framework.exception.ClientException("请重新读取control-state后提交新命令",
                    com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode.VERSION_CONFLICT);
        RedTeamTask task = find(tenant,actor,run.getTaskId()).orElseThrow();
        if (task.executionMode()!=com.kant.agent.contract.probe.ExecutionMode.ADAPTIVE_MULTI_TURN
                || java.util.Set.of("COMPLETED","FAILED","STOPPED").contains(run.getStatus())
                || Boolean.TRUE.equals(run.getStopRequested()) && request.type()!=com.kant.llm.eval.agent.redteam.dto.req.RedTeamRunCommandRequest.Type.STOP)
            throw new com.kant.framework.exception.ClientException("当前模式或运行状态不允许该命令",
                    com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode.RUN_STATE_CONFLICT);
        try {
            switch (request.type()) {
                case PAUSE -> requestPause(tenant,actor,runId);
                case RESUME -> {
                    // 幂等重放在此之前已返回；只有首次恢复才重新核对快照与证据。
                    resumeValidator.run();
                    if (!toInstant(run.getStartedAt()).plusSeconds(task.budgetPolicy().maxDurationSeconds()).isAfter(Instant.now()))
                        throw new IllegalStateException("原运行已过期");
                    requestResume(tenant,actor,runId);
                }
                case STOP -> requestStop(tenant,actor,runId);
            }
        } catch (IllegalStateException ex) {
            throw new com.kant.framework.exception.ClientException("当前状态不能受理控制命令",
                    com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode.RUN_STATE_CONFLICT);
        }
        var entity = new com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamCommandDO();
        entity.setCommandId("command_"+java.util.UUID.randomUUID().toString().replace("-",""));
        entity.setRunId(runId); entity.setActorId(actor); entity.setType(request.type().name());
        entity.setIdempotencyKey(request.idempotencyKey()); entity.setExpectedVersion(request.expectedVersion());
        entity.setAcceptedVersion(findRunEntity(tenant,actor,runId).orElseThrow().getVersion());
        entity.setCreatedAt(toLocalDateTime(Instant.now())); commandMapper.insert(entity);
        return receipt(entity);
    }

    private com.kant.llm.eval.agent.redteam.dto.resp.RedTeamCommandReceipt receipt(
            com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamCommandDO entity) {
        return new com.kant.llm.eval.agent.redteam.dto.resp.RedTeamCommandReceipt(entity.getCommandId(),entity.getRunId(),
                entity.getType(),entity.getAcceptedVersion(),toInstant(entity.getCreatedAt()));
    }

    /** 一致读取状态与事件游标；只读事务默认沿用数据库隔离级别。 */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public com.kant.llm.eval.agent.redteam.dto.resp.RedTeamControlState controlState(String tenant,String actor,String runId) {
        AgentRedTeamRunDO run = requireAuthorizedRun(tenant,actor,runId);
        var last = eventMapper.selectOne(new LambdaQueryWrapper<com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamEventDO>()
                .eq(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamEventDO::getRunId,runId)
                .orderByDesc(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamEventDO::getId).last("LIMIT 1"));
        return new com.kant.llm.eval.agent.redteam.dto.resp.RedTeamControlState(runId,run.getStatus(),defaultLong(run.getVersion()),
                last==null?0:last.getId(),Boolean.TRUE.equals(run.getStopRequested()));
    }

    /** 游标查询不会触发目标执行，最多返回100个安全事件。 */
    public List<com.kant.llm.eval.agent.redteam.dto.resp.RedTeamRunEventResponse> listEvents(String tenant,String actor,String runId,long after) {
        AgentRedTeamRunDO run = requireAuthorizedRun(tenant,actor,runId);
        return eventMapper.selectList(new LambdaQueryWrapper<com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamEventDO>()
                .eq(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamEventDO::getRunId,runId)
                .gt(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamEventDO::getId,after)
                .orderByAsc(com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamEventDO::getId).last("LIMIT 100"))
                .stream().map(e -> new com.kant.llm.eval.agent.redteam.dto.resp.RedTeamRunEventResponse(e.getId(),e.getRunVersion(),runId,
                        run.getTaskId(),e.getEventType(),e.getStatus(),runId,e.getSummary(),toInstant(e.getCreatedAt()),"1.0")).toList();
    }

    private AgentRedTeamRunDO requireAuthorizedRun(String tenant,String actor,String runId) {
        return findRunEntity(tenant,actor,runId).orElseThrow(() -> new com.kant.framework.exception.ClientException("运行不存在或无权访问",
                com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode.RUN_NOT_FOUND));
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

    private AttackSession toSession(AgentRedTeamSessionDO entity) {
        return new AttackSession(
                entity.getSessionId(),
                entity.getTaskId(),
                entity.getRunId(),
                entity.getAttemptId(),
                entity.getTenantId(),
                entity.getActorId(),
                entity.getTargetSnapshotRef(),
                AttackSessionStatus.valueOf(entity.getStatus()),
                defaultInt(entity.getTurnCount()),
                defaultInt(entity.getNextSequence()),
                entity.getLastProbeId(),
                entity.getLastExecutionId(),
                Boolean.TRUE.equals(entity.getPauseRequested()),
                Boolean.TRUE.equals(entity.getStopRequested()),
                defaultLong(entity.getCheckpointVersion()),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt()),
                toInstant(entity.getClosedAt()),
                entity.getErrorCode(),
                entity.getErrorMessage());
    }

    private SessionCheckpoint toCheckpoint(AgentRedTeamSessionCheckpointDO entity) {
        return new SessionCheckpoint(
                entity.getCheckpointId(),
                entity.getSessionId(),
                entity.getTaskId(),
                entity.getRunId(),
                entity.getAttemptId(),
                defaultLong(entity.getCheckpointVersion()),
                defaultInt(entity.getTurnCount()),
                defaultInt(entity.getNextSequence()),
                entity.getLastProbeId(),
                entity.getLastExecutionId(),
                entity.getHistoryHash(),
                AttackSessionStatus.valueOf(entity.getStatus()),
                toInstant(entity.getCreatedAt()));
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

    private void copySession(AttackSession session, AgentRedTeamSessionDO entity) {
        entity.setSessionId(session.sessionId());
        entity.setTaskId(session.taskId());
        entity.setRunId(session.runId());
        entity.setAttemptId(session.attemptId());
        entity.setTenantId(session.tenantId());
        entity.setActorId(session.actorId());
        entity.setTargetSnapshotRef(session.targetSnapshotRef());
        entity.setStatus(session.status().name());
        entity.setTurnCount(session.turnCount());
        entity.setNextSequence(session.nextSequence());
        entity.setLastProbeId(session.lastProbeId());
        entity.setLastExecutionId(session.lastExecutionId());
        entity.setPauseRequested(session.pauseRequested());
        entity.setStopRequested(session.stopRequested());
        entity.setCheckpointVersion(session.checkpointVersion());
        entity.setCreatedAt(toLocalDateTime(session.createdAt()));
        entity.setUpdatedAt(toLocalDateTime(session.updatedAt()));
        entity.setClosedAt(toLocalDateTime(session.closedAt()));
        entity.setErrorCode(session.errorCode());
        entity.setErrorMessage(session.errorMessage());
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
