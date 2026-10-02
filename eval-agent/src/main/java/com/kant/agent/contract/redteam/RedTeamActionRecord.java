package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.probe.ProbeExecutionStatus;

import java.time.Instant;
import java.util.List;

/**
 * 攻击动作的审计记录。
 *
 * <p>动作记录只保存输入摘要和证据引用，不复制输入及响应正文。原文由 D01
 * 的受限证据表保存。</p>
 */
public record RedTeamActionRecord(
        String actionId,
        String taskId,
        String runId,
        String attemptId,
        int sequence,
        AttackActionType actionType,
        String strategyVersion,
        String inputHash,
        String reasonSummary,
        List<String> observationRefs,
        AttackStopReason suggestedStopReason,
        AttackStopReason actualStopReason,
        String inputEvidenceRef,
        String probeId,
        String executionId,
        ProbeExecutionStatus executionStatus,
        String responseEvidenceRef,
        String errorCode,
        boolean riskObserved,
        String actionStatus,
        Instant createdAt) {

    /** 校验动作审计字段并冻结引用。 */
    public RedTeamActionRecord {
        actionId = ContractChecks.maxLength(ContractChecks.notBlank(actionId, "actionId"), 96, "actionId");
        taskId = ContractChecks.maxLength(ContractChecks.notBlank(taskId, "taskId"), 96, "taskId");
        runId = ContractChecks.maxLength(ContractChecks.notBlank(runId, "runId"), 96, "runId");
        attemptId = ContractChecks.maxLength(ContractChecks.notBlank(attemptId, "attemptId"), 96, "attemptId");
        ContractChecks.positive(sequence, "sequence");
        actionType = ContractChecks.required(actionType, "actionType");
        strategyVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(strategyVersion, "strategyVersion"), 128, "strategyVersion");
        inputHash = ContractChecks.maxLength(ContractChecks.nullableText(inputHash), 64, "inputHash");
        reasonSummary = ContractChecks.maxLength(
                ContractChecks.notBlank(reasonSummary, "reasonSummary"), 2_000, "reasonSummary");
        observationRefs = observationRefs == null ? List.of() : List.copyOf(observationRefs);
        inputEvidenceRef = ContractChecks.maxLength(
                ContractChecks.nullableText(inputEvidenceRef), 96, "inputEvidenceRef");
        probeId = ContractChecks.maxLength(ContractChecks.nullableText(probeId), 64, "probeId");
        executionId = ContractChecks.maxLength(ContractChecks.nullableText(executionId), 96, "executionId");
        responseEvidenceRef = ContractChecks.maxLength(
                ContractChecks.nullableText(responseEvidenceRef), 96, "responseEvidenceRef");
        errorCode = ContractChecks.maxLength(ContractChecks.nullableText(errorCode), 64, "errorCode");
        actionStatus = ContractChecks.maxLength(
                ContractChecks.notBlank(actionStatus, "actionStatus"), 32, "actionStatus");
        createdAt = ContractChecks.required(createdAt, "createdAt");
    }
}
