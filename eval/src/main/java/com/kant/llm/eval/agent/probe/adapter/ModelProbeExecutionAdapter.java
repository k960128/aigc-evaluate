package com.kant.llm.eval.agent.probe.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.agent.contract.probe.ExecutionMode;
import com.kant.agent.contract.probe.ProbeCancellation;
import com.kant.agent.contract.probe.ProbeCommand;
import com.kant.agent.contract.probe.ProbeLookup;
import com.kant.agent.contract.probe.ProbeSubmission;
import com.kant.agent.contract.probe.TargetObservation;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.contract.target.TargetStatus;
import com.kant.agent.contract.target.TargetType;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.port.target.TargetCapabilityPort;
import com.kant.ai.infra.client.ClientModelInfo;
import com.kant.ai.infra.client.ModelClientStrategy;
import com.kant.ai.infra.client.ModelClientStrategyFactory;
import com.kant.ai.infra.client.ModelRequest;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.framework.exception.ClientException;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.eval.agent.probe.dao.entity.AgentProbeExecutionDO;
import com.kant.llm.eval.agent.probe.error.AgentProbeErrorCode;
import com.kant.llm.eval.agent.probe.service.AgentProbeRecordService;
import com.kant.llm.eval.agent.probe.service.AgentProbeRecordService.NewProbeAcceptance;
import com.kant.llm.eval.agent.probe.support.ProbeDigestService;
import com.kant.llm.eval.agent.probe.support.ProbeIdentifierGenerator;
import com.kant.llm.eval.agent.probe.support.SensitiveErrorSanitizer;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.dto.resp.ModelInfoVO;
import com.kant.llm.eval.basesource.service.ModelInfoService;
import com.kant.llm.eval.pipeline.service.ratelimit.ModelCallPermit;
import com.kant.llm.eval.pipeline.service.ratelimit.ModelCallRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * D01 无状态模型目标的共享探测执行适配器。
 *
 * <p>执行顺序固定为：检查幂等记录、验证目标能力、持久化输入和执行意图、
 * 推进 RUNNING、取得并发许可、调用目标、保存响应或错误证据。进入模型客户端
 * 调用后发生的异常统一保存为 UNKNOWN，且本实现不自动重发。</p>
 */
@Slf4j
@Component
public class ModelProbeExecutionAdapter implements ProbeExecutionPort {

    private static final String ERROR_DEADLINE_EXPIRED = "DEADLINE_EXPIRED_BEFORE_CALL";
    private static final String ERROR_TARGET_CONFIGURATION_CHANGED = "TARGET_CONFIGURATION_CHANGED";
    private static final String ERROR_TARGET_PREPARATION_FAILED = "TARGET_PREPARATION_FAILED";
    private static final String ERROR_TARGET_CALL_UNKNOWN = "TARGET_CALL_RESULT_UNKNOWN";

    private final AgentProbeRecordService recordService;
    private final TargetCapabilityPort targetCapabilityPort;
    private final ModelTargetSnapshotFactory snapshotFactory;
    private final ModelInfoService modelInfoService;
    private final ModelCallRateLimiter modelCallRateLimiter;
    private final ModelClientStrategyFactory strategyFactory;
    private final ProbeDigestService digestService;
    private final ProbeIdentifierGenerator identifierGenerator;
    private final SensitiveErrorSanitizer errorSanitizer;
    private final ObjectMapper objectMapper;

    public ModelProbeExecutionAdapter(AgentProbeRecordService recordService,
                                      TargetCapabilityPort targetCapabilityPort,
                                      ModelTargetSnapshotFactory snapshotFactory,
                                      ModelInfoService modelInfoService,
                                      ModelCallRateLimiter modelCallRateLimiter,
                                      ModelClientStrategyFactory strategyFactory,
                                      ProbeDigestService digestService,
                                      ProbeIdentifierGenerator identifierGenerator,
                                      SensitiveErrorSanitizer errorSanitizer,
                                      ObjectMapper objectMapper) {
        this.recordService = recordService;
        this.targetCapabilityPort = targetCapabilityPort;
        this.snapshotFactory = snapshotFactory;
        this.modelInfoService = modelInfoService;
        this.modelCallRateLimiter = modelCallRateLimiter;
        this.strategyFactory = strategyFactory;
        this.digestService = digestService;
        this.identifierGenerator = identifierGenerator;
        this.errorSanitizer = errorSanitizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProbeSubmission submit(ProbeCommand command) {
        String fingerprint = digestService.requestFingerprint(command);
        Optional<AgentProbeExecutionDO> existing = recordService.findByIdempotencyKey(
                command.context().tenantId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), fingerprint);
        }

        TargetSnapshot snapshot = targetCapabilityPort.getSnapshot(
                command.context().tenantId(), command.context().actorId(), command.target());
        validateD01Request(command, snapshot);
        if (command.deadlineAt() != null && !command.deadlineAt().isAfter(Instant.now())) {
            throw new ClientException("探测截止时间已过，未受理目标调用",
                    AgentProbeErrorCode.INVALID_PROBE_REQUEST);
        }

        String executionId = identifierGenerator.executionId();
        String inputEvidenceRef = identifierGenerator.inputEvidenceRef();
        Instant acceptedAt = Instant.now();
        try {
            recordService.accept(new NewProbeAcceptance(
                    command,
                    snapshot,
                    serializeSnapshot(snapshot),
                    fingerprint,
                    executionId,
                    inputEvidenceRef,
                    acceptedAt));
        } catch (DuplicateKeyException ex) {
            // 并发请求可能同时通过首次查询，唯一索引决定唯一胜者。
            AgentProbeExecutionDO winner = recordService.findByIdempotencyKey(
                            command.context().tenantId(), command.idempotencyKey())
                    .orElseThrow(() -> new ServiceException(
                            "幂等竞争后未找到已受理探测",
                            AgentProbeErrorCode.PROBE_PERSISTENCE_ERROR));
            return resolveExisting(winner, fingerprint);
        }

        boolean running = recordService.markRunning(
                command.context().tenantId(), executionId, Instant.now());
        if (!running) {
            recordService.markCancelledBeforeCall(
                    command.context().tenantId(), executionId, Instant.now());
            return loadSubmission(command.context().tenantId(), command.context().actorId(), executionId, true);
        }

        executeTarget(command, snapshot, executionId);
        return loadSubmission(command.context().tenantId(), command.context().actorId(), executionId, true);
    }

    @Override
    public Optional<TargetObservation> find(ProbeLookup lookup) {
        return recordService.findAuthorized(lookup.tenantId(), lookup.actorId(), lookup.executionId())
                .map(recordService::toObservation);
    }

    @Override
    public ProbeCancellation requestCancellation(ProbeLookup lookup) {
        AgentProbeExecutionDO current = recordService.findAuthorized(
                        lookup.tenantId(), lookup.actorId(), lookup.executionId())
                .orElseThrow(() -> new ClientException(
                        "探测执行不存在，executionId=" + lookup.executionId(),
                        AgentProbeErrorCode.PROBE_NOT_FOUND));
        return recordService.requestCancellation(current, Instant.now());
    }

    /**
     * 在执行意图已经持久化后调用被测模型。
     */
    private void executeTarget(ProbeCommand command, TargetSnapshot acceptedSnapshot, String executionId) {
        Instant executionStartedAt = Instant.now();
        boolean targetCallStarted = false;
        try {
            if (command.deadlineAt() != null && !command.deadlineAt().isAfter(Instant.now())) {
                failWithoutTargetCall(command, executionId, ERROR_DEADLINE_EXPIRED,
                        "探测在开始目标调用前超过截止时间", executionStartedAt);
                return;
            }

            ModelInfoDO modelInfo = loadEnabledModel(command.target().targetId());
            TargetSnapshot currentSnapshot = snapshotFactory.create(modelInfo);
            if (!acceptedSnapshot.snapshotRef().equals(currentSnapshot.snapshotRef())) {
                failWithoutTargetCall(command, executionId, ERROR_TARGET_CONFIGURATION_CHANGED,
                        "目标配置在受理后发生变化，未执行模型调用", executionStartedAt);
                return;
            }

            ClientModelInfo clientModelInfo = toClientModelInfo(modelInfo);
            ModelClientStrategy strategy = strategyFactory.getStrategy(clientModelInfo);
            if (strategy.getManufacturer() != clientModelInfo.getManufacturerType()) {
                failWithoutTargetCall(command, executionId, ERROR_TARGET_CONFIGURATION_CHANGED,
                        "模型厂商配置已变化，需要刷新目标适配器", executionStartedAt);
                return;
            }

            ModelInfoVO modelInfoVO = toModelInfoVO(modelInfo);
            try (ModelCallPermit ignored = modelCallRateLimiter.acquire(modelInfoVO)) {
                targetCallStarted = true;
                ModelResponse response = strategy.call(ModelRequest.builder()
                        .clientModelInfo(clientModelInfo)
                        .inputText(command.input())
                        .build());
                if (response == null || response.getRespContent() == null) {
                    throw new IllegalStateException("目标客户端未返回可确认的响应内容");
                }
                long latencyMs = resolveLatency(response, executionStartedAt);
                recordService.completeSuccess(
                        command.context().tenantId(),
                        command.context().actorId(),
                        executionId,
                        identifierGenerator.responseEvidenceRef(),
                        response.getRespContent(),
                        latencyMs,
                        Instant.now());
                log.info("动态探测目标调用完成，executionId: {}, traceId: {}, targetId: {}, status: SUCCEEDED",
                        executionId, command.context().traceId(), command.target().targetId());
            }
        } catch (Exception ex) {
            String safeMessage = targetCallStarted
                    ? "目标调用可能已经执行，但平台未获得可确认结果"
                    : errorSanitizer.sanitize(ex);
            String errorCode = targetCallStarted
                    ? ERROR_TARGET_CALL_UNKNOWN
                    : ERROR_TARGET_PREPARATION_FAILED;
            recordService.completeFailure(
                    command.context().tenantId(),
                    command.context().actorId(),
                    executionId,
                    identifierGenerator.errorEvidenceRef(),
                    errorCode,
                    safeMessage,
                    targetCallStarted,
                    elapsedMillis(executionStartedAt),
                    Instant.now());
            // 只记录业务标识和异常类型，避免把输入、响应或第三方凭据写入普通日志。
            log.warn("动态探测执行未成功，executionId: {}, traceId: {}, targetId: {}, "
                            + "targetCallStarted: {}, errorType: {}",
                    executionId,
                    command.context().traceId(),
                    command.target().targetId(),
                    targetCallStarted,
                    ex.getClass().getSimpleName());
        }
    }

    private void failWithoutTargetCall(ProbeCommand command,
                                       String executionId,
                                       String errorCode,
                                       String message,
                                       Instant executionStartedAt) {
        recordService.completeFailure(
                command.context().tenantId(),
                command.context().actorId(),
                executionId,
                identifierGenerator.errorEvidenceRef(),
                errorCode,
                message,
                false,
                elapsedMillis(executionStartedAt),
                Instant.now());
    }

    private ProbeSubmission resolveExisting(AgentProbeExecutionDO existing, String requestFingerprint) {
        if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw new ClientException(
                    "同一幂等键已用于不同探测参数，idempotencyKey=" + existing.getIdempotencyKey(),
                    AgentProbeErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return recordService.toSubmission(existing, false);
    }

    private ProbeSubmission loadSubmission(String tenantId,
                                           String actorId,
                                           String executionId,
                                           boolean newlyAccepted) {
        AgentProbeExecutionDO execution = recordService.findAuthorized(tenantId, actorId, executionId)
                .orElseThrow(() -> new ServiceException(
                        "探测执行完成后无法读取记录，executionId=" + executionId,
                        AgentProbeErrorCode.PROBE_PERSISTENCE_ERROR));
        return recordService.toSubmission(execution, newlyAccepted);
    }

    private void validateD01Request(ProbeCommand command, TargetSnapshot snapshot) {
        if (command.target().targetType() != TargetType.MODEL) {
            throw new ClientException("D01仅支持MODEL类型目标",
                    AgentProbeErrorCode.TARGET_CAPABILITY_UNSUPPORTED);
        }
        if (command.executionMode() != ExecutionMode.STATIC_SINGLE_TURN) {
            throw new ClientException("D01仅支持STATIC_SINGLE_TURN静态单轮探测",
                    AgentProbeErrorCode.TARGET_CAPABILITY_UNSUPPORTED);
        }
        if (snapshot.status() != TargetStatus.AVAILABLE) {
            throw new ClientException("模型目标未启用，targetId=" + command.target().targetId(),
                    AgentProbeErrorCode.TARGET_UNAVAILABLE);
        }
        Set<TargetCapability> required = EnumSet.of(TargetCapability.SINGLE_TURN);
        required.addAll(command.requiredCapabilities());
        if (!snapshot.capabilities().supportsAll(required)) {
            String unsupported = required.stream()
                    .filter(capability -> !snapshot.capabilities().supports(capability))
                    .map(Enum::name)
                    .sorted()
                    .collect(Collectors.joining(","));
            throw new ClientException("目标不支持请求能力：" + unsupported,
                    AgentProbeErrorCode.TARGET_CAPABILITY_UNSUPPORTED);
        }
    }

    private ModelInfoDO loadEnabledModel(String targetId) {
        Long modelId;
        try {
            modelId = Long.valueOf(targetId);
        } catch (NumberFormatException ex) {
            throw new ClientException("模型目标ID必须是有效数字",
                    AgentProbeErrorCode.INVALID_PROBE_REQUEST);
        }
        ModelInfoDO modelInfo = modelInfoService.getById(modelId);
        if (modelInfo == null || !Boolean.TRUE.equals(modelInfo.getStatus())) {
            throw new ClientException("模型配置不存在或未启用，modelId=" + modelId,
                    AgentProbeErrorCode.TARGET_UNAVAILABLE);
        }
        return modelInfo;
    }

    private ClientModelInfo toClientModelInfo(ModelInfoDO modelInfo) {
        ModelManufacturerEnum manufacturer;
        try {
            manufacturer = ModelManufacturerEnum.valueOf(
                    modelInfo.getManufacturerCode().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new ClientException("无法识别模型厂商配置",
                    AgentProbeErrorCode.TARGET_UNAVAILABLE);
        }
        return ClientModelInfo.builder()
                .modelId(modelInfo.getId())
                .model(modelInfo.getModel())
                .apiKey(modelInfo.getApiKey())
                .baseUrl(modelInfo.getBaseUrl())
                .manufacturerType(manufacturer)
                .build();
    }

    private ModelInfoVO toModelInfoVO(ModelInfoDO modelInfo) {
        return ModelInfoVO.builder()
                .id(modelInfo.getId())
                .model(modelInfo.getModel())
                .baseUrl(modelInfo.getBaseUrl())
                .apiKey(modelInfo.getApiKey())
                .modelDescribe(modelInfo.getModelDescribe())
                .manufacturerCode(modelInfo.getManufacturerCode())
                .maxThreadSize(modelInfo.getMaxThreadSize())
                .maxCompletionTokens(modelInfo.getMaxCompletionTokens())
                .stream(modelInfo.getStream())
                .status(modelInfo.getStatus())
                .config(modelInfo.getConfig())
                .version(modelInfo.getVersion())
                .createTime(modelInfo.getCreateTime())
                .updateTime(modelInfo.getUpdateTime())
                .build();
    }

    private String serializeSnapshot(TargetSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new ServiceException("目标快照序列化失败",
                    AgentProbeErrorCode.PROBE_PERSISTENCE_ERROR);
        }
    }

    private long resolveLatency(ModelResponse response, Instant executionStartedAt) {
        if (response.getElapsed() != null && response.getElapsed() >= 0) {
            return response.getElapsed();
        }
        return elapsedMillis(executionStartedAt);
    }

    private long elapsedMillis(Instant startedAt) {
        return Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
    }
}
