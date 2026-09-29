package com.kant.agent.contract.probe;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.evidence.EvidenceReference;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.contract.target.TargetReference;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 一次目标探测形成的权威执行观察。
 *
 * <p>观察只陈述执行事实，不等同于安全判定。风险结论由独立的
 * {@code AssessmentPort} 基于证据引用生成。</p>
 *
 * @param context               执行业务上下文
 * @param reference             逻辑探测与物理执行引用
 * @param idempotencyKey        首次受理时使用的幂等键
 * @param status                当前执行状态
 * @param target                被测目标引用
 * @param targetSnapshotRef     本次实际使用的不可变目标快照引用
 * @param riskId                探测对应的风险编码
 * @param ruleSetVersion        后续判定规则集版本
 * @param executionMode         目标交互模式
 * @param requiredCapabilities  调用方明确要求的额外目标能力
 * @param sessionRef            目标会话引用；单轮执行为空
 * @param deadlineAt            最晚开始目标调用时间；未设置时为空
 * @param inputEvidence         输入证据引用
 * @param responseEvidence      目标响应证据引用；成功时必填
 * @param errorEvidence         错误证据引用；失败或未知时可用
 * @param toolTraceEvidence     工具轨迹证据引用列表
 * @param retrievalTraceEvidence 检索轨迹证据引用列表
 * @param evidenceCompleteness  证据完整程度
 * @param latencyMs             目标执行耗时，单位毫秒；尚未结束时可为空
 * @param usage                 本次执行的消耗记录
 * @param errorCode             稳定错误编码；正常成功时为空
 * @param errorMessage          可安全展示的错误摘要；不包含凭据和原始响应
 * @param cancellationRequested 是否曾收到取消意图
 * @param acceptedAt            执行意图首次受理时间
 * @param startedAt             实际开始执行时间
 * @param finishedAt            执行结束时间
 */
public record TargetObservation(
        ExecutionContext context,
        ProbeExecutionReference reference,
        String idempotencyKey,
        ProbeExecutionStatus status,
        TargetReference target,
        String targetSnapshotRef,
        String riskId,
        String ruleSetVersion,
        ExecutionMode executionMode,
        Set<TargetCapability> requiredCapabilities,
        String sessionRef,
        Instant deadlineAt,
        EvidenceReference inputEvidence,
        EvidenceReference responseEvidence,
        EvidenceReference errorEvidence,
        List<EvidenceReference> toolTraceEvidence,
        List<EvidenceReference> retrievalTraceEvidence,
        EvidenceCompleteness evidenceCompleteness,
        Long latencyMs,
        UsageRecord usage,
        String errorCode,
        String errorMessage,
        boolean cancellationRequested,
        Instant acceptedAt,
        Instant startedAt,
        Instant finishedAt) {

    /**
     * 校验观察对象的状态不变量并冻结证据列表。
     */
    public TargetObservation {
        context = ContractChecks.required(context, "context");
        reference = ContractChecks.required(reference, "reference");
        idempotencyKey = ContractChecks.maxLength(
                ContractChecks.notBlank(idempotencyKey, "idempotencyKey"), 128, "idempotencyKey");
        status = ContractChecks.required(status, "status");
        target = ContractChecks.required(target, "target");
        targetSnapshotRef = ContractChecks.maxLength(
                ContractChecks.notBlank(targetSnapshotRef, "targetSnapshotRef"), 160, "targetSnapshotRef");
        riskId = ContractChecks.maxLength(
                ContractChecks.notBlank(riskId, "riskId"), 128, "riskId");
        ruleSetVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(ruleSetVersion, "ruleSetVersion"), 128, "ruleSetVersion");
        executionMode = ContractChecks.required(executionMode, "executionMode");
        if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
            requiredCapabilities = Collections.emptySet();
        } else {
            requiredCapabilities = Collections.unmodifiableSet(EnumSet.copyOf(requiredCapabilities));
        }
        sessionRef = ContractChecks.maxLength(
                ContractChecks.nullableText(sessionRef), 96, "sessionRef");
        inputEvidence = ContractChecks.required(inputEvidence, "inputEvidence");
        toolTraceEvidence = toolTraceEvidence == null ? List.of() : List.copyOf(toolTraceEvidence);
        retrievalTraceEvidence = retrievalTraceEvidence == null ? List.of() : List.copyOf(retrievalTraceEvidence);
        evidenceCompleteness = ContractChecks.required(evidenceCompleteness, "evidenceCompleteness");
        if (latencyMs != null) {
            ContractChecks.nonNegative(latencyMs, "latencyMs");
        }
        usage = ContractChecks.required(usage, "usage");
        errorCode = ContractChecks.maxLength(
                ContractChecks.nullableText(errorCode), 64, "errorCode");
        errorMessage = ContractChecks.maxLength(
                ContractChecks.nullableText(errorMessage), 1000, "errorMessage");
        acceptedAt = ContractChecks.required(acceptedAt, "acceptedAt");

        if (status == ProbeExecutionStatus.SUCCEEDED) {
            ContractChecks.required(responseEvidence, "responseEvidence");
            ContractChecks.require(errorCode == null, "执行成功时不能设置errorCode");
        }
        if (status == ProbeExecutionStatus.FAILED || status == ProbeExecutionStatus.UNKNOWN) {
            ContractChecks.require(errorCode != null, "执行失败或结果未知时必须设置errorCode");
            ContractChecks.require(finishedAt != null, "执行失败或结果未知时必须设置finishedAt");
        }
        if (status.isTerminal()) {
            ContractChecks.require(finishedAt != null, "终态执行必须设置finishedAt");
        }
    }
}
