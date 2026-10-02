package com.kant.llm.eval.agent.probe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.evidence.EvidenceDataClass;
import com.kant.agent.contract.evidence.EvidenceReference;
import com.kant.agent.contract.evidence.EvidenceType;
import com.kant.agent.contract.probe.EvidenceCompleteness;
import com.kant.agent.contract.probe.ExecutionMode;
import com.kant.agent.contract.probe.ProbeCancellation;
import com.kant.agent.contract.probe.ProbeCommand;
import com.kant.agent.contract.probe.ProbeExecutionReference;
import com.kant.agent.contract.probe.ProbeExecutionStatus;
import com.kant.agent.contract.probe.ProbeSubmission;
import com.kant.agent.contract.probe.TargetObservation;
import com.kant.agent.contract.probe.UsageMeasurement;
import com.kant.agent.contract.probe.UsageRecord;
import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.contract.target.TargetType;
import com.kant.agent.contract.target.TargetSessionMessage;
import com.kant.agent.contract.target.TargetSessionMessageRole;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.eval.agent.probe.dao.entity.AgentEvidenceDO;
import com.kant.llm.eval.agent.probe.dao.entity.AgentProbeExecutionDO;
import com.kant.llm.eval.agent.probe.dao.mapper.AgentEvidenceMapper;
import com.kant.llm.eval.agent.probe.dao.mapper.AgentProbeExecutionMapper;
import com.kant.llm.eval.agent.probe.error.AgentProbeErrorCode;
import com.kant.llm.eval.agent.probe.support.ProbeDigestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态探测执行事实和证据的事务服务。
 *
 * <p>每个状态变更均在独立短事务中完成，外部模型调用绝不持有数据库事务。
 * 这样既能保证“先持久化意图再调用目标”，也避免长事务占用连接和锁。</p>
 */
@Service
public class AgentProbeRecordService {

    /** 证据所属对象类型。 */
    private static final String PROBE_EXECUTION_OWNER = "PROBE_EXECUTION";

    private final AgentProbeExecutionMapper executionMapper;
    private final AgentEvidenceMapper evidenceMapper;
    private final ProbeDigestService digestService;
    private final com.kant.llm.eval.agent.redteam.service.AgentRedTeamRecordService redTeamRecords;
    private final com.kant.llm.eval.agent.validation.service.ValidationRunRecordService validationRecords;

    public AgentProbeRecordService(AgentProbeExecutionMapper executionMapper,
                                   AgentEvidenceMapper evidenceMapper,
                                   ProbeDigestService digestService,
                                   com.kant.llm.eval.agent.redteam.service.AgentRedTeamRecordService redTeamRecords,
                                   com.kant.llm.eval.agent.validation.service.ValidationRunRecordService validationRecords) {
        this.executionMapper = executionMapper;
        this.evidenceMapper = evidenceMapper;
        this.digestService = digestService;
        this.redTeamRecords = redTeamRecords;
        this.validationRecords = validationRecords;
    }

    /**
     * 按租户和幂等键查询既有执行。
     *
     * @param tenantId      租户或业务空间
     * @param idempotencyKey 幂等键
     * @return 既有执行记录
     */
    public Optional<AgentProbeExecutionDO> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return Optional.ofNullable(executionMapper.selectOne(
                new LambdaQueryWrapper<AgentProbeExecutionDO>()
                        .eq(AgentProbeExecutionDO::getTenantId, tenantId)
                        .eq(AgentProbeExecutionDO::getIdempotencyKey, idempotencyKey)
                        .last("LIMIT 1")));
    }

    /**
     * 按租户、操作者和执行标识查询记录。
     */
    public Optional<AgentProbeExecutionDO> findAuthorized(String tenantId,
                                                          String actorId,
                                                          String executionId) {
        return Optional.ofNullable(executionMapper.selectOne(
                new LambdaQueryWrapper<AgentProbeExecutionDO>()
                        .eq(AgentProbeExecutionDO::getTenantId, tenantId)
                        .eq(AgentProbeExecutionDO::getActorId, actorId)
                        .eq(AgentProbeExecutionDO::getExecutionId, executionId)
                        .last("LIMIT 1")));
    }

    /**
     * 在同一事务中保存输入证据和 ACCEPTED 执行意图。
     *
     * <p>数据库唯一索引负责解决并发幂等竞争。唯一键冲突会向调用方抛出，
     * 由端口适配器在事务回滚后读取胜出的既有记录。</p>
     *
     * @param acceptance 新执行所需的完整数据
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accept(NewProbeAcceptance acceptance) {
        boolean validation=acceptance.command().context().runId().startsWith("vrun_");
        if(validation) validationRecords.requireWorkerForProbe(acceptance.command());
        if (acceptance.command().executionMode() == ExecutionMode.ADAPTIVE_MULTI_TURN) {
            // 与执行意图插入处于同一短事务，旧Worker失去租约后不能再次首次受理。
            if(!validation) redTeamRecords.requireWorkerForProbe(acceptance.command());
            long inFlight = executionMapper.selectCount(new LambdaQueryWrapper<AgentProbeExecutionDO>()
                    .eq(AgentProbeExecutionDO::getSessionRef, acceptance.command().sessionRef())
                    .in(AgentProbeExecutionDO::getStatus, ProbeExecutionStatus.ACCEPTED.name(),
                            ProbeExecutionStatus.RUNNING.name(), ProbeExecutionStatus.UNKNOWN.name(),
                            ProbeExecutionStatus.CANCELLATION_REQUESTED.name()));
            if (inFlight != 0) throw new IllegalStateException("同一会话存在未确认操作");
        }
        LocalDateTime acceptedAt = toLocalDateTime(acceptance.acceptedAt());
        insertEvidence(
                acceptance.inputEvidenceRef(),
                acceptance.command().context().tenantId(),
                acceptance.command().context().actorId(),
                acceptance.executionId(),
                EvidenceType.PROBE_INPUT,
                "text/plain; charset=UTF-8",
                acceptance.command().input(),
                EvidenceDataClass.RESTRICTED,
                acceptedAt);

        ProbeCommand command = acceptance.command();
        ExecutionContext context = command.context();
        AgentProbeExecutionDO execution = new AgentProbeExecutionDO();
        execution.setTenantId(context.tenantId());
        execution.setActorId(context.actorId());
        execution.setActivityId(context.activityId());
        execution.setTaskId(context.taskId());
        execution.setRunId(context.runId());
        execution.setAttemptId(context.attemptId());
        execution.setProbeId(command.probeId());
        execution.setExecutionId(acceptance.executionId());
        execution.setTraceId(context.traceId());
        execution.setAuthorizationRef(context.authorizationRef());
        execution.setIdempotencyKey(command.idempotencyKey());
        execution.setRequestFingerprint(acceptance.requestFingerprint());
        execution.setTargetType(command.target().targetType().name());
        execution.setTargetId(command.target().targetId());
        execution.setTargetSnapshotRef(acceptance.targetSnapshot().snapshotRef());
        execution.setTargetSnapshotJson(acceptance.targetSnapshotJson());
        execution.setRiskId(command.riskId());
        execution.setRuleSetVersion(command.ruleSetVersion());
        execution.setExecutionMode(command.executionMode().name());
        execution.setRequiredCapabilities(command.requiredCapabilities().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(",")));
        execution.setSessionRef(command.sessionRef());
        execution.setDeadlineAt(command.deadlineAt() == null
                ? null
                : toLocalDateTime(command.deadlineAt()));
        execution.setInputEvidenceRef(acceptance.inputEvidenceRef());
        execution.setStatus(ProbeExecutionStatus.ACCEPTED.name());
        execution.setEvidenceCompleteness(EvidenceCompleteness.PARTIAL.name());
        execution.setTargetCalls(0L);
        execution.setAgentCalls(0L);
        execution.setUsageMeasurement(UsageMeasurement.UNAVAILABLE.name());
        execution.setCancellationRequested(false);
        execution.setAcceptedAt(acceptedAt);
        execution.setVersion(0L);
        execution.setCreateTime(acceptedAt);
        execution.setUpdateTime(acceptedAt);
        execution.setDeleted(false);
        executionMapper.insert(execution);
    }

    /**
     * 将 ACCEPTED 执行原子推进为 RUNNING。
     *
     * @return 状态推进成功时返回 {@code true}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRunning(String tenantId, String executionId, Instant startedAt) {
        LocalDateTime time = toLocalDateTime(startedAt);
        int changed = executionMapper.update(null,
                new LambdaUpdateWrapper<AgentProbeExecutionDO>()
                        .eq(AgentProbeExecutionDO::getTenantId, tenantId)
                        .eq(AgentProbeExecutionDO::getExecutionId, executionId)
                        .eq(AgentProbeExecutionDO::getStatus, ProbeExecutionStatus.ACCEPTED.name())
                        .set(AgentProbeExecutionDO::getStatus, ProbeExecutionStatus.RUNNING.name())
                        .set(AgentProbeExecutionDO::getStartedAt, time)
                        .set(AgentProbeExecutionDO::getUpdateTime, time)
                        .setSql("version = version + 1"));
        return changed == 1;
    }

    /**
     * 当执行尚未开始便收到取消意图时，将其原子推进为 CANCELLED。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markCancelledBeforeCall(String tenantId, String executionId, Instant finishedAt) {
        LocalDateTime time = toLocalDateTime(finishedAt);
        int changed = executionMapper.update(null,
                new LambdaUpdateWrapper<AgentProbeExecutionDO>()
                        .eq(AgentProbeExecutionDO::getTenantId, tenantId)
                        .eq(AgentProbeExecutionDO::getExecutionId, executionId)
                        .eq(AgentProbeExecutionDO::getStatus, ProbeExecutionStatus.CANCELLATION_REQUESTED.name())
                        .isNull(AgentProbeExecutionDO::getStartedAt)
                        .set(AgentProbeExecutionDO::getStatus, ProbeExecutionStatus.CANCELLED.name())
                        .set(AgentProbeExecutionDO::getFinishedAt, time)
                        .set(AgentProbeExecutionDO::getUpdateTime, time)
                        .setSql("version = version + 1"));
        return changed == 1;
    }

    /**
     * 保存目标响应证据并将执行推进为 SUCCEEDED。
     *
     * @param usage 本次目标调用的真实或估算消耗；不能省略
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeSuccess(String tenantId,
                                String actorId,
                                String executionId,
                                String responseEvidenceRef,
                                String responseContent,
                                long latencyMs,
                                UsageRecord usage,
                                Instant finishedAt) {
        usage = ContractChecks.required(usage, "usage");
        LocalDateTime time = toLocalDateTime(finishedAt);
        insertEvidence(
                responseEvidenceRef,
                tenantId,
                actorId,
                executionId,
                EvidenceType.TARGET_RESPONSE,
                "text/plain; charset=UTF-8",
                responseContent == null ? "" : responseContent,
                EvidenceDataClass.RESTRICTED,
                time);

        int changed = executionMapper.update(null,
                new LambdaUpdateWrapper<AgentProbeExecutionDO>()
                        .eq(AgentProbeExecutionDO::getTenantId, tenantId)
                        .eq(AgentProbeExecutionDO::getExecutionId, executionId)
                        .in(AgentProbeExecutionDO::getStatus,
                                ProbeExecutionStatus.RUNNING.name(),
                                ProbeExecutionStatus.CANCELLATION_REQUESTED.name())
                        .set(AgentProbeExecutionDO::getStatus, ProbeExecutionStatus.SUCCEEDED.name())
                        .set(AgentProbeExecutionDO::getResponseEvidenceRef, responseEvidenceRef)
                        .set(AgentProbeExecutionDO::getEvidenceCompleteness, EvidenceCompleteness.COMPLETE.name())
                        .set(AgentProbeExecutionDO::getLatencyMs, Math.max(0L, latencyMs))
                        .set(AgentProbeExecutionDO::getTargetCalls, usage.targetCalls())
                        .set(AgentProbeExecutionDO::getAgentCalls, usage.agentCalls())
                        .set(AgentProbeExecutionDO::getInputTokens, usage.inputTokens())
                        .set(AgentProbeExecutionDO::getOutputTokens, usage.outputTokens())
                        .set(AgentProbeExecutionDO::getCost, usage.cost())
                        .set(AgentProbeExecutionDO::getCurrency, usage.currency())
                        .set(AgentProbeExecutionDO::getUsageMeasurement, usage.measurement().name())
                        .set(AgentProbeExecutionDO::getFinishedAt, time)
                        .set(AgentProbeExecutionDO::getUpdateTime, time)
                        .setSql("version = version + 1"));
        ensureSingleStateChange(changed, executionId, "完成目标响应");
    }

    /**
     * 保存受控错误证据并将执行推进为 FAILED 或 UNKNOWN。
     *
     * @param targetCallStarted 是否已经进入目标客户端调用；为真时只能保存 UNKNOWN
     * @param usage             本次目标调用的真实或估算消耗；不能省略
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeFailure(String tenantId,
                                String actorId,
                                String executionId,
                                String errorEvidenceRef,
                                String errorCode,
                                String safeErrorMessage,
                                boolean targetCallStarted,
                                long latencyMs,
                                UsageRecord usage,
                                Instant finishedAt) {
        usage = ContractChecks.required(usage, "usage");
        LocalDateTime time = toLocalDateTime(finishedAt);
        ProbeExecutionStatus finalStatus = targetCallStarted
                ? ProbeExecutionStatus.UNKNOWN
                : ProbeExecutionStatus.FAILED;
        String errorContent = "errorCode=" + errorCode + System.lineSeparator()
                + "message=" + safeErrorMessage;
        insertEvidence(
                errorEvidenceRef,
                tenantId,
                actorId,
                executionId,
                EvidenceType.TARGET_ERROR,
                "text/plain; charset=UTF-8",
                errorContent,
                EvidenceDataClass.RESTRICTED,
                time);

        int changed = executionMapper.update(null,
                new LambdaUpdateWrapper<AgentProbeExecutionDO>()
                        .eq(AgentProbeExecutionDO::getTenantId, tenantId)
                        .eq(AgentProbeExecutionDO::getExecutionId, executionId)
                        .in(AgentProbeExecutionDO::getStatus,
                                ProbeExecutionStatus.ACCEPTED.name(),
                                ProbeExecutionStatus.RUNNING.name(),
                                ProbeExecutionStatus.CANCELLATION_REQUESTED.name())
                        .set(AgentProbeExecutionDO::getStatus, finalStatus.name())
                        .set(AgentProbeExecutionDO::getErrorEvidenceRef, errorEvidenceRef)
                        .set(AgentProbeExecutionDO::getErrorCode, errorCode)
                        .set(AgentProbeExecutionDO::getErrorMessage, safeErrorMessage)
                        .set(AgentProbeExecutionDO::getEvidenceCompleteness, EvidenceCompleteness.PARTIAL.name())
                        .set(AgentProbeExecutionDO::getLatencyMs, Math.max(0L, latencyMs))
                        .set(AgentProbeExecutionDO::getTargetCalls, usage.targetCalls())
                        .set(AgentProbeExecutionDO::getAgentCalls, usage.agentCalls())
                        .set(AgentProbeExecutionDO::getInputTokens, usage.inputTokens())
                        .set(AgentProbeExecutionDO::getOutputTokens, usage.outputTokens())
                        .set(AgentProbeExecutionDO::getCost, usage.cost())
                        .set(AgentProbeExecutionDO::getCurrency, usage.currency())
                        .set(AgentProbeExecutionDO::getUsageMeasurement, usage.measurement().name())
                        .set(AgentProbeExecutionDO::getFinishedAt, time)
                        .set(AgentProbeExecutionDO::getUpdateTime, time)
                        .setSql("version = version + 1"));
        ensureSingleStateChange(changed, executionId, "保存执行失败");
    }

    /**
     * 记录取消意图并返回最新状态。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProbeCancellation requestCancellation(AgentProbeExecutionDO current, Instant requestedAt) {
        ProbeExecutionStatus currentStatus = ProbeExecutionStatus.valueOf(current.getStatus());
        if (currentStatus.isTerminal() || Boolean.TRUE.equals(current.getCancellationRequested())) {
            return toCancellation(current);
        }

        LocalDateTime time = toLocalDateTime(requestedAt);
        executionMapper.update(null,
                new LambdaUpdateWrapper<AgentProbeExecutionDO>()
                        .eq(AgentProbeExecutionDO::getTenantId, current.getTenantId())
                        .eq(AgentProbeExecutionDO::getActorId, current.getActorId())
                        .eq(AgentProbeExecutionDO::getExecutionId, current.getExecutionId())
                        .in(AgentProbeExecutionDO::getStatus,
                                ProbeExecutionStatus.ACCEPTED.name(),
                                ProbeExecutionStatus.RUNNING.name())
                        .set(AgentProbeExecutionDO::getCancellationRequested, true)
                        .set(AgentProbeExecutionDO::getCancellationRequestedAt, time)
                        .set(AgentProbeExecutionDO::getStatus,
                                ProbeExecutionStatus.CANCELLATION_REQUESTED.name())
                        .set(AgentProbeExecutionDO::getUpdateTime, time)
                        .setSql("version = version + 1"));

        AgentProbeExecutionDO latest = findAuthorized(
                current.getTenantId(), current.getActorId(), current.getExecutionId())
                .orElseThrow(() -> new ServiceException(
                        "记录取消意图后无法读取探测执行",
                        AgentProbeErrorCode.PROBE_PERSISTENCE_ERROR));
        return toCancellation(latest);
    }

    /**
     * 将数据库事实转换为探测提交结果。
     */
    public ProbeSubmission toSubmission(AgentProbeExecutionDO execution, boolean newlyAccepted) {
        return new ProbeSubmission(
                new ProbeExecutionReference(execution.getProbeId(), execution.getExecutionId()),
                ProbeExecutionStatus.valueOf(execution.getStatus()),
                newlyAccepted,
                toInstant(execution.getAcceptedAt()));
    }

    /**
     * 将数据库事实和证据索引转换为领域观察。
     */
    public TargetObservation toObservation(AgentProbeExecutionDO execution) {
        EvidenceReference inputEvidence = loadEvidenceReference(execution.getInputEvidenceRef())
                .orElseThrow(() -> new ServiceException(
                        "探测输入证据缺失，executionId=" + execution.getExecutionId(),
                        AgentProbeErrorCode.PROBE_PERSISTENCE_ERROR));
        EvidenceReference responseEvidence = loadEvidenceReference(execution.getResponseEvidenceRef()).orElse(null);
        EvidenceReference errorEvidence = loadEvidenceReference(execution.getErrorEvidenceRef()).orElse(null);

        ExecutionContext context = new ExecutionContext(
                execution.getTenantId(),
                execution.getActorId(),
                execution.getActivityId(),
                execution.getTaskId(),
                execution.getRunId(),
                execution.getAttemptId(),
                execution.getTraceId(),
                execution.getAuthorizationRef());
        UsageMeasurement measurement = UsageMeasurement.valueOf(execution.getUsageMeasurement());
        UsageRecord usage = new UsageRecord(
                defaultLong(execution.getTargetCalls()),
                defaultLong(execution.getAgentCalls()),
                execution.getInputTokens(),
                execution.getOutputTokens(),
                execution.getCost(),
                execution.getCurrency(),
                measurement);
        return new TargetObservation(
                context,
                new ProbeExecutionReference(execution.getProbeId(), execution.getExecutionId()),
                execution.getIdempotencyKey(),
                ProbeExecutionStatus.valueOf(execution.getStatus()),
                new TargetReference(
                        TargetType.valueOf(execution.getTargetType()),
                        execution.getTargetId()),
                execution.getTargetSnapshotRef(),
                execution.getRiskId(),
                execution.getRuleSetVersion(),
                ExecutionMode.valueOf(execution.getExecutionMode()),
                parseCapabilities(execution.getRequiredCapabilities()),
                execution.getSessionRef(),
                toInstant(execution.getDeadlineAt()),
                inputEvidence,
                responseEvidence,
                errorEvidence,
                List.of(),
                List.of(),
                EvidenceCompleteness.valueOf(execution.getEvidenceCompleteness()),
                execution.getLatencyMs(),
                usage,
                execution.getErrorCode(),
                execution.getErrorMessage(),
                Boolean.TRUE.equals(execution.getCancellationRequested()),
                toInstant(execution.getAcceptedAt()),
                toInstant(execution.getStartedAt()),
                toInstant(execution.getFinishedAt()));
    }

    /**
     * 读取指定证据索引，不返回正文。
     */
    public Optional<EvidenceReference> loadEvidenceReference(String evidenceRef) {
        if (evidenceRef == null || evidenceRef.isBlank()) {
            return Optional.empty();
        }
        AgentEvidenceDO evidence = evidenceMapper.selectOne(
                new LambdaQueryWrapper<AgentEvidenceDO>()
                        .eq(AgentEvidenceDO::getEvidenceRef, evidenceRef)
                        .last("LIMIT 1"));
        return Optional.ofNullable(evidence).map(this::toEvidenceReference);
    }

    /**
     * 根据已经完成的 D01 探测事实重建一条线性目标会话的消息历史。
     *
     * <p>只读取同一租户、操作者和 sessionRef 下的成功执行；UNKNOWN、FAILED
     * 或尚未终态的执行不能进入下一轮上下文。正文仍然来自受限证据表，
     * 不从运行内存读取。</p>
     */
    public List<TargetSessionMessage> listSessionMessages(
            String tenantId,
            String actorId,
            String sessionRef) {
        if (sessionRef == null || sessionRef.isBlank()) {
            return List.of();
        }
        List<AgentProbeExecutionDO> executions = executionMapper.selectList(
                new LambdaQueryWrapper<AgentProbeExecutionDO>()
                        .eq(AgentProbeExecutionDO::getTenantId, tenantId)
                        .eq(AgentProbeExecutionDO::getActorId, actorId)
                        .eq(AgentProbeExecutionDO::getSessionRef, sessionRef)
                        .eq(AgentProbeExecutionDO::getStatus,
                                ProbeExecutionStatus.SUCCEEDED.name())
                        // 同一会话受理串行，数据库递增主键消除毫秒时间戳并列歧义。
                        .orderByAsc(AgentProbeExecutionDO::getId));
        List<TargetSessionMessage> messages = new java.util.ArrayList<>();
        int sequence = 1;
        for (AgentProbeExecutionDO execution : executions) {
            AgentEvidenceDO input = findEvidence(
                    tenantId, actorId, execution.getExecutionId(), execution.getInputEvidenceRef());
            AgentEvidenceDO response = findEvidence(
                    tenantId, actorId, execution.getExecutionId(), execution.getResponseEvidenceRef());
            if (input == null || response == null) {
                // 会话历史必须保持完整的 user/assistant 对。静默跳过会让后续
                // 请求在缺失上下文的情况下继续，形成不可解释的“伪多轮”。
                throw new IllegalStateException(
                        "多轮会话存在缺失输入或响应证据，executionId=" + execution.getExecutionId());
            }
            if (!EvidenceType.PROBE_INPUT.name().equals(input.getEvidenceType())
                    || !EvidenceType.TARGET_RESPONSE.name().equals(response.getEvidenceType())
                    || !digestService.contentHash(input.getContent()).equals(input.getContentHash())
                    || !digestService.contentHash(response.getContent()).equals(response.getContentHash())) {
                throw new IllegalStateException("多轮证据类型或正文摘要不一致");
            }
            messages.add(new TargetSessionMessage(
                    sequence++, TargetSessionMessageRole.USER, input.getContent()));
            messages.add(new TargetSessionMessage(
                    sequence++, TargetSessionMessageRole.ASSISTANT, response.getContent()));
        }
        return List.copyOf(messages);
    }

    /** 按执行归属读取证据正文，避免仅凭 evidenceRef 跨执行读取。 */
    private AgentEvidenceDO findEvidence(
            String tenantId,
            String actorId,
            String executionId,
            String evidenceRef) {
        if (evidenceRef == null || evidenceRef.isBlank()) {
            return null;
        }
        return evidenceMapper.selectOne(new LambdaQueryWrapper<AgentEvidenceDO>()
                .eq(AgentEvidenceDO::getTenantId, tenantId)
                .eq(AgentEvidenceDO::getActorId, actorId)
                .eq(AgentEvidenceDO::getOwnerType, PROBE_EXECUTION_OWNER)
                .eq(AgentEvidenceDO::getOwnerRef, executionId)
                .eq(AgentEvidenceDO::getEvidenceRef, evidenceRef)
                .last("LIMIT 1"));
    }

    /**
     * 将证据实体转换为不含正文的索引。
     */
    public EvidenceReference toEvidenceReference(AgentEvidenceDO evidence) {
        return new EvidenceReference(
                evidence.getEvidenceRef(),
                EvidenceType.valueOf(evidence.getEvidenceType()),
                evidence.getOwnerType(),
                evidence.getOwnerRef(),
                evidence.getMediaType(),
                evidence.getContentHash(),
                EvidenceDataClass.valueOf(evidence.getDataClass()),
                toInstant(evidence.getCreateTime()));
    }

    private void insertEvidence(String evidenceRef,
                                String tenantId,
                                String actorId,
                                String ownerRef,
                                EvidenceType evidenceType,
                                String mediaType,
                                String content,
                                EvidenceDataClass dataClass,
                                LocalDateTime createTime) {
        AgentEvidenceDO evidence = new AgentEvidenceDO();
        evidence.setEvidenceRef(evidenceRef);
        evidence.setTenantId(tenantId);
        evidence.setActorId(actorId);
        evidence.setOwnerType(PROBE_EXECUTION_OWNER);
        evidence.setOwnerRef(ownerRef);
        evidence.setEvidenceType(evidenceType.name());
        evidence.setMediaType(mediaType);
        evidence.setContent(content);
        evidence.setContentHash(digestService.contentHash(content));
        evidence.setDataClass(dataClass.name());
        evidence.setCreateTime(createTime);
        evidence.setDeleted(false);
        evidenceMapper.insert(evidence);
    }

    /** 调用前的短事务控制检查；返回后才进入外部模型客户端。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkMultiTurnCallBoundary(ProbeCommand command) {
        // 固定单轮验证也需要租约、停止和截止时间围栏，不只检查多轮探索。
        if(command.context().runId().startsWith("vrun_")) validationRecords.requireWorkerForProbe(command);
        else redTeamRecords.requireWorkerForProbe(command);
    }

    private ProbeCancellation toCancellation(AgentProbeExecutionDO execution) {
        return new ProbeCancellation(
                new ProbeExecutionReference(execution.getProbeId(), execution.getExecutionId()),
                ProbeExecutionStatus.valueOf(execution.getStatus()),
                Boolean.TRUE.equals(execution.getCancellationRequested()),
                toInstant(execution.getCancellationRequestedAt()));
    }

    private void ensureSingleStateChange(int changed, String executionId, String action) {
        if (changed != 1) {
            throw new ServiceException(
                    action + "时探测状态已变化，executionId=" + executionId,
                    AgentProbeErrorCode.PROBE_STATE_CONFLICT);
        }
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private Set<TargetCapability> parseCapabilities(String capabilities) {
        if (capabilities == null || capabilities.isBlank()) {
            return Set.of();
        }
        EnumSet<TargetCapability> result = EnumSet.noneOf(TargetCapability.class);
        for (String value : capabilities.split(",")) {
            if (!value.isBlank()) {
                result.add(TargetCapability.valueOf(value.trim()));
            }
        }
        return result;
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * 首次受理探测时传入事务服务的不可变参数。
     *
     * @param command             原始探测命令
     * @param targetSnapshot      受理时获取的目标快照
     * @param targetSnapshotJson  不含凭据的快照 JSON
     * @param requestFingerprint  规范化请求指纹
     * @param executionId         新物理执行标识
     * @param inputEvidenceRef    新输入证据引用
     * @param acceptedAt          受理时间
     */
    public record NewProbeAcceptance(
            ProbeCommand command,
            TargetSnapshot targetSnapshot,
            String targetSnapshotJson,
            String requestFingerprint,
            String executionId,
            String inputEvidenceRef,
            Instant acceptedAt) {
    }
}
