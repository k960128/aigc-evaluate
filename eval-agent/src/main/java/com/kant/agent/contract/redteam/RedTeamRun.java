package com.kant.agent.contract.redteam;

import com.kant.agent.contract.budget.BudgetSnapshot;
import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;

/**
 * 自动红队一次运行的可恢复状态摘要。
 */
public record RedTeamRun(
        String runId,
        String taskId,
        String tenantId,
        String actorId,
        RedTeamRunStatus status,
        BudgetSnapshot budget,
        int actionCount,
        String currentAttemptId,
        AttackStopReason stopReason,
        String stopMessage,
        boolean stopRequested,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    /** 校验运行摘要中的身份和计数。 */
    public RedTeamRun {
        runId = ContractChecks.maxLength(ContractChecks.notBlank(runId, "runId"), 96, "runId");
        taskId = ContractChecks.maxLength(ContractChecks.notBlank(taskId, "taskId"), 96, "taskId");
        tenantId = ContractChecks.maxLength(ContractChecks.notBlank(tenantId, "tenantId"), 64, "tenantId");
        actorId = ContractChecks.maxLength(ContractChecks.notBlank(actorId, "actorId"), 64, "actorId");
        status = ContractChecks.required(status, "status");
        budget = ContractChecks.required(budget, "budget");
        ContractChecks.nonNegative(actionCount, "actionCount");
        currentAttemptId = ContractChecks.maxLength(
                ContractChecks.nullableText(currentAttemptId), 96, "currentAttemptId");
        stopMessage = ContractChecks.maxLength(ContractChecks.nullableText(stopMessage), 1_000, "stopMessage");
        createdAt = ContractChecks.required(createdAt, "createdAt");
    }
}
