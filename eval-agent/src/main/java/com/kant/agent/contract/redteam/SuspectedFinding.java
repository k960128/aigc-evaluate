package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;
import java.util.List;

/**
 * D02 根据明确规则信号生成的疑似发现。
 *
 * <p>该对象永远不是人工确认结果。后续 D04 的复核和复现流程可以把它升级为
 * 确认发现，但不能覆盖本条原始记录。</p>
 */
public record SuspectedFinding(
        String findingId,
        String taskId,
        String runId,
        String attemptId,
        String actionId,
        String riskId,
        List<String> evidenceRefs,
        String reason,
        String verificationStatus,
        Instant createdAt) {

    /** 校验疑似发现必须引用可追溯证据。 */
    public SuspectedFinding {
        findingId = ContractChecks.maxLength(ContractChecks.notBlank(findingId, "findingId"), 96, "findingId");
        taskId = ContractChecks.maxLength(ContractChecks.notBlank(taskId, "taskId"), 96, "taskId");
        runId = ContractChecks.maxLength(ContractChecks.notBlank(runId, "runId"), 96, "runId");
        attemptId = ContractChecks.maxLength(ContractChecks.notBlank(attemptId, "attemptId"), 96, "attemptId");
        actionId = ContractChecks.maxLength(ContractChecks.notBlank(actionId, "actionId"), 96, "actionId");
        riskId = ContractChecks.maxLength(ContractChecks.notBlank(riskId, "riskId"), 128, "riskId");
        evidenceRefs = List.copyOf(ContractChecks.notEmpty(evidenceRefs, "evidenceRefs"));
        reason = ContractChecks.maxLength(ContractChecks.notBlank(reason, "reason"), 2_000, "reason");
        verificationStatus = ContractChecks.maxLength(
                ContractChecks.notBlank(verificationStatus, "verificationStatus"), 32, "verificationStatus");
        ContractChecks.require("SUSPECTED".equals(verificationStatus),
                "D02疑似发现的verificationStatus必须为SUSPECTED");
        createdAt = ContractChecks.required(createdAt, "createdAt");
    }
}
