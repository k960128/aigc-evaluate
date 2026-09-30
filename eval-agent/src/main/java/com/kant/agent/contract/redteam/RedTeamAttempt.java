package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;

/**
 * 一个风险和策略组合下的攻击尝试。
 */
public record RedTeamAttempt(
        String attemptId,
        String taskId,
        String runId,
        int sequence,
        String riskId,
        String strategyVersion,
        RedTeamRunStatus status,
        int probeCount,
        AttackStopReason stopReason,
        String stopMessage,
        Instant startedAt,
        Instant finishedAt) {

    /** 校验尝试标识和计数。 */
    public RedTeamAttempt {
        attemptId = ContractChecks.maxLength(ContractChecks.notBlank(attemptId, "attemptId"), 96, "attemptId");
        taskId = ContractChecks.maxLength(ContractChecks.notBlank(taskId, "taskId"), 96, "taskId");
        runId = ContractChecks.maxLength(ContractChecks.notBlank(runId, "runId"), 96, "runId");
        ContractChecks.positive(sequence, "sequence");
        riskId = ContractChecks.maxLength(ContractChecks.notBlank(riskId, "riskId"), 128, "riskId");
        strategyVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(strategyVersion, "strategyVersion"), 128, "strategyVersion");
        status = ContractChecks.required(status, "status");
        ContractChecks.nonNegative(probeCount, "probeCount");
        stopMessage = ContractChecks.maxLength(ContractChecks.nullableText(stopMessage), 1_000, "stopMessage");
        startedAt = ContractChecks.required(startedAt, "startedAt");
    }
}
