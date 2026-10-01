package com.kant.agent.contract.redteam;

import com.kant.agent.contract.budget.BudgetPolicy;
import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.probe.ExecutionMode;
import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetType;

import java.time.Instant;
import java.util.List;

/**
 * 已保存的自动红队任务配置快照。
 */
public record RedTeamTask(
        String taskId,
        String idempotencyKey,
        String tenantId,
        String actorId,
        String activityId,
        String authorizationRef,
        TargetReference target,
        String targetSnapshotRef,
        String riskId,
        String ruleSetVersion,
        ExecutionMode executionMode,
        String strategyVersion,
        String seedInput,
        List<String> riskIndicators,
        BudgetPolicy budgetPolicy,
        RedTeamTaskStatus status,
        AttackStopReason stopReason,
        String stopMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    /** 冻结任务中的配置集合并校验状态字段。 */
    public RedTeamTask {
        taskId = ContractChecks.maxLength(ContractChecks.notBlank(taskId, "taskId"), 96, "taskId");
        idempotencyKey = ContractChecks.maxLength(
                ContractChecks.notBlank(idempotencyKey, "idempotencyKey"), 128, "idempotencyKey");
        tenantId = ContractChecks.maxLength(ContractChecks.notBlank(tenantId, "tenantId"), 64, "tenantId");
        actorId = ContractChecks.maxLength(ContractChecks.notBlank(actorId, "actorId"), 64, "actorId");
        activityId = ContractChecks.maxLength(
                ContractChecks.notBlank(activityId, "activityId"), 64, "activityId");
        authorizationRef = ContractChecks.maxLength(
                ContractChecks.notBlank(authorizationRef, "authorizationRef"), 128, "authorizationRef");
        target = ContractChecks.required(target, "target");
        ContractChecks.require(target.targetType() == TargetType.MODEL,
                "D02当前只支持MODEL类型目标");
        targetSnapshotRef = ContractChecks.maxLength(
                ContractChecks.notBlank(targetSnapshotRef, "targetSnapshotRef"), 160, "targetSnapshotRef");
        riskId = ContractChecks.maxLength(ContractChecks.notBlank(riskId, "riskId"), 128, "riskId");
        ruleSetVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(ruleSetVersion, "ruleSetVersion"), 128, "ruleSetVersion");
        executionMode = ContractChecks.required(executionMode, "executionMode");
        ContractChecks.require(
                executionMode == ExecutionMode.ADAPTIVE_SINGLE_TURN
                        || executionMode == ExecutionMode.ADAPTIVE_MULTI_TURN,
                "当前只支持ADAPTIVE_SINGLE_TURN或ADAPTIVE_MULTI_TURN");
        strategyVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(strategyVersion, "strategyVersion"), 128, "strategyVersion");
        seedInput = ContractChecks.maxLength(
                ContractChecks.notBlankPreserving(seedInput, "seedInput"), 100_000, "seedInput");
        // 任务可能来自数据库，而不是只经过创建命令。这里再次执行 D02
        // 约束，避免历史脏数据或人工写库绕过创建入口后进入攻击循环。
        riskIndicators = normalizeRiskIndicators(riskIndicators);
        budgetPolicy = ContractChecks.required(budgetPolicy, "budgetPolicy");
        validateD02Budget(budgetPolicy);
        status = ContractChecks.required(status, "status");
        stopMessage = ContractChecks.maxLength(ContractChecks.nullableText(stopMessage), 1_000, "stopMessage");
        createdAt = ContractChecks.required(createdAt, "createdAt");
    }

    /** 将创建命令转换为等待运行的任务快照。 */
    public static RedTeamTask ready(RedTeamTaskCommand command, Instant createdAt) {
        return new RedTeamTask(
                command.taskId(),
                command.idempotencyKey(),
                command.tenantId(),
                command.actorId(),
                command.activityId(),
                command.authorizationRef(),
                command.target(),
                command.targetSnapshotRef(),
                command.riskId(),
                command.ruleSetVersion(),
                command.executionMode(),
                command.strategyVersion(),
                command.seedInput(),
                command.riskIndicators(),
                command.budgetPolicy(),
                RedTeamTaskStatus.READY,
                null,
                null,
                createdAt,
                null,
                null);
    }

    /** 规范化并校验数据库快照中的风险信号。 */
    private static List<String> normalizeRiskIndicators(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(
                    "riskIndicators不能为空，D02需要明确的疑似风险信号");
        }
        List<String> normalized = values.stream()
                .map(value -> ContractChecks.maxLength(
                        ContractChecks.notBlank(value, "riskIndicators项"),
                        256,
                        "riskIndicators项"))
                .distinct()
                .toList();
        ContractChecks.require(normalized.size() <= 20, "riskIndicators不能超过20项");
        return List.copyOf(normalized);
    }

    /** 校验 D02 当前只允许的预算维度。 */
    private static void validateD02Budget(BudgetPolicy policy) {
        ContractChecks.require(policy.maxAttempts() == 1,
                "D02当前只支持一个AttackAttempt，maxAttempts必须为1");
        ContractChecks.require(policy.maxConcurrentSessions() == 1,
                "D02当前只支持一个串行尝试，maxConcurrentSessions必须为1");
        ContractChecks.require(policy.maxTokens() > 0,
                "D02会调用被测模型，maxTokens必须大于0");
        ContractChecks.require(policy.maxCost() == null,
                "D02当前没有统一费用计量，不能配置maxCost");
    }
}
