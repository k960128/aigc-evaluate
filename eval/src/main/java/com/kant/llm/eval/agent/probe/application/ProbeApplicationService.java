package com.kant.llm.eval.agent.probe.application;

import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.evidence.EvidenceContent;
import com.kant.agent.contract.evidence.EvidenceQuery;
import com.kant.agent.contract.probe.ProbeCancellation;
import com.kant.agent.contract.probe.ProbeCommand;
import com.kant.agent.contract.probe.ProbeLookup;
import com.kant.agent.contract.probe.ProbeSubmission;
import com.kant.agent.contract.probe.TargetObservation;
import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.contract.target.TargetType;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.port.target.TargetCapabilityPort;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.dto.req.SubmitProbeRequest;
import com.kant.llm.eval.agent.probe.error.AgentProbeErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * D01 探测 API 的应用服务。
 *
 * <p>该服务负责从认证上下文取得身份、组装纯领域命令并调用端口，不接触
 * 模型凭据、数据库实体或 Spring AI 类型。</p>
 */
@Service
public class ProbeApplicationService {

    private final ProbePrincipalProvider principalProvider;
    private final ProbeExecutionPort probeExecutionPort;
    private final EvidenceReadPort evidenceReadPort;
    private final TargetCapabilityPort targetCapabilityPort;

    public ProbeApplicationService(ProbePrincipalProvider principalProvider,
                                   ProbeExecutionPort probeExecutionPort,
                                   EvidenceReadPort evidenceReadPort,
                                   TargetCapabilityPort targetCapabilityPort) {
        this.principalProvider = principalProvider;
        this.probeExecutionPort = probeExecutionPort;
        this.evidenceReadPort = evidenceReadPort;
        this.targetCapabilityPort = targetCapabilityPort;
    }

    /** 提交一次受控模型探测。 */
    public ProbeSubmission submit(SubmitProbeRequest request) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        ExecutionContext context = new ExecutionContext(
                principal.tenantId(),
                principal.actorId(),
                request.getActivityId(),
                request.getTaskId(),
                request.getRunId(),
                request.getAttemptId(),
                newTraceId(),
                request.getAuthorizationRef());
        ProbeCommand command = new ProbeCommand(
                request.getIdempotencyKey(),
                context,
                request.getProbeId(),
                new TargetReference(TargetType.MODEL, String.valueOf(request.getTargetModelId())),
                request.getRiskId(),
                request.getRuleSetVersion(),
                request.getExecutionMode(),
                null,
                request.getInput(),
                Optional.ofNullable(request.getRequiredCapabilities()).orElse(Set.of()),
                request.getDeadlineAt());
        return probeExecutionPort.submit(command);
    }

    /** 查询当前用户创建的一次物理探测。 */
    public TargetObservation getExecution(String executionId) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        return probeExecutionPort.find(new ProbeLookup(
                        principal.tenantId(), principal.actorId(), executionId))
                .orElseThrow(() -> new ClientException(
                        "探测执行不存在，executionId=" + executionId,
                        AgentProbeErrorCode.PROBE_NOT_FOUND));
    }

    /** 读取当前用户指定执行所属的原始证据。 */
    public EvidenceContent getEvidence(String executionId, String evidenceRef) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        return evidenceReadPort.read(new EvidenceQuery(
                        principal.tenantId(), principal.actorId(), executionId, evidenceRef))
                .orElseThrow(() -> new ClientException(
                        "证据不存在或无权访问，evidenceRef=" + evidenceRef,
                        AgentProbeErrorCode.EVIDENCE_NOT_FOUND));
    }

    /** 记录一次探测的取消意图。 */
    public ProbeCancellation requestCancellation(String executionId) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        return probeExecutionPort.requestCancellation(new ProbeLookup(
                principal.tenantId(), principal.actorId(), executionId));
    }

    /** 读取一个模型目标当前真实声明的能力。 */
    public TargetSnapshot getModelCapabilities(Long modelId) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        return targetCapabilityPort.getSnapshot(
                principal.tenantId(),
                principal.actorId(),
                new TargetReference(TargetType.MODEL, String.valueOf(modelId)));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
