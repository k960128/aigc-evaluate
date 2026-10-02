package com.kant.llm.eval.agent.probe.dto.resp;

import com.kant.agent.contract.probe.TargetObservation;

import java.time.Instant;
import java.util.List;

/**
 * 单次物理探测的完整查询响应。
 *
 * @param tenantId               执行所属租户或业务空间
 * @param actorId                发起执行的操作者
 * @param activityId             评测活动标识
 * @param taskId                 评测任务标识
 * @param runId                  本次运行标识
 * @param attemptId              攻击尝试标识
 * @param probeId                逻辑探测标识
 * @param executionId            物理执行标识
 * @param idempotencyKey         首次受理时使用的幂等键
 * @param traceId                技术链路追踪标识
 * @param authorizationRef       本次执行使用的授权引用
 * @param status                 ACCEPTED、RUNNING、SUCCEEDED、FAILED、UNKNOWN 等状态
 * @param targetType             被测目标类型
 * @param targetId               现有目标资源标识
 * @param targetSnapshotRef      本次执行固定的目标快照引用
 * @param riskId                 风险编码
 * @param ruleSetVersion         后续判定规则集版本
 * @param executionMode          目标交互模式
 * @param requiredCapabilities   调用方明确要求的额外目标能力
 * @param sessionRef             目标会话引用；D01 单轮执行为 {@code null}
 * @param deadlineAt             最晚开始目标调用时间；未设置时为空
 * @param inputEvidence          原始输入证据索引
 * @param responseEvidence       目标响应证据索引；未成功时可能为空
 * @param errorEvidence          受控错误证据索引；无错误时为空
 * @param toolTraceEvidence      工具轨迹证据索引；D01 为空列表
 * @param retrievalTraceEvidence 检索轨迹证据索引；D01 为空列表
 * @param evidenceCompleteness   COMPLETE、PARTIAL 或 MISSING
 * @param latencyMs              目标执行耗时毫秒；尚未结束时可能为空
 * @param usage                  真实可得的调用、Token 和费用消耗
 * @param errorCode              稳定错误编码；成功时为空
 * @param errorMessage           可安全展示的错误摘要；成功时为空
 * @param cancellationRequested  是否曾收到取消意图
 * @param acceptedAt             执行意图首次受理时间
 * @param startedAt              执行层开始处理时间
 * @param finishedAt             执行进入终态的时间
 */
public record ProbeExecutionResponse(
        String tenantId,
        String actorId,
        String activityId,
        String taskId,
        String runId,
        String attemptId,
        String probeId,
        String executionId,
        String idempotencyKey,
        String traceId,
        String authorizationRef,
        String status,
        String targetType,
        String targetId,
        String targetSnapshotRef,
        String riskId,
        String ruleSetVersion,
        String executionMode,
        List<String> requiredCapabilities,
        String sessionRef,
        Instant deadlineAt,
        EvidenceReferenceResponse inputEvidence,
        EvidenceReferenceResponse responseEvidence,
        EvidenceReferenceResponse errorEvidence,
        List<EvidenceReferenceResponse> toolTraceEvidence,
        List<EvidenceReferenceResponse> retrievalTraceEvidence,
        String evidenceCompleteness,
        Long latencyMs,
        UsageResponse usage,
        String errorCode,
        String errorMessage,
        boolean cancellationRequested,
        Instant acceptedAt,
        Instant startedAt,
        Instant finishedAt) {

    /** 将领域执行观察转换为 API 响应。 */
    public static ProbeExecutionResponse from(TargetObservation observation) {
        return new ProbeExecutionResponse(
                observation.context().tenantId(),
                observation.context().actorId(),
                observation.context().activityId(),
                observation.context().taskId(),
                observation.context().runId(),
                observation.context().attemptId(),
                observation.reference().probeId(),
                observation.reference().executionId(),
                observation.idempotencyKey(),
                observation.context().traceId(),
                observation.context().authorizationRef(),
                observation.status().name(),
                observation.target().targetType().name(),
                observation.target().targetId(),
                observation.targetSnapshotRef(),
                observation.riskId(),
                observation.ruleSetVersion(),
                observation.executionMode().name(),
                observation.requiredCapabilities().stream().map(Enum::name).sorted().toList(),
                observation.sessionRef(),
                observation.deadlineAt(),
                EvidenceReferenceResponse.from(observation.inputEvidence()),
                EvidenceReferenceResponse.from(observation.responseEvidence()),
                EvidenceReferenceResponse.from(observation.errorEvidence()),
                observation.toolTraceEvidence().stream().map(EvidenceReferenceResponse::from).toList(),
                observation.retrievalTraceEvidence().stream().map(EvidenceReferenceResponse::from).toList(),
                observation.evidenceCompleteness().name(),
                observation.latencyMs(),
                UsageResponse.from(observation.usage()),
                observation.errorCode(),
                observation.errorMessage(),
                observation.cancellationRequested(),
                observation.acceptedAt(),
                observation.startedAt(),
                observation.finishedAt());
    }
}
