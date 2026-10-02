package com.kant.agent.contract.redteam;

import com.kant.agent.contract.budget.BudgetPolicy;
import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.probe.ExecutionMode;
import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetType;

import java.util.List;

/**
 * 创建 D02 自动红队任务的固定配置命令。
 *
 * <p>D02 使用自适应单轮模式；D03 在同一任务契约上增加线性自适应多轮模式。
 * 两个模式仍然只允许一个风险、一个固定策略和一个串行尝试。</p>
 */
public record RedTeamTaskCommand(
        String idempotencyKey,
        String taskId,
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
        BudgetPolicy budgetPolicy) {

    /** 校验 D02 创建配置并冻结风险信号列表。 */
    public RedTeamTaskCommand {
        idempotencyKey = ContractChecks.maxLength(
                ContractChecks.notBlank(idempotencyKey, "idempotencyKey"), 128, "idempotencyKey");
        taskId = ContractChecks.maxLength(ContractChecks.notBlank(taskId, "taskId"), 96, "taskId");
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
        ContractChecks.require(
                executionMode == ExecutionMode.ADAPTIVE_SINGLE_TURN
                        || executionMode == ExecutionMode.ADAPTIVE_MULTI_TURN,
                "当前只支持ADAPTIVE_SINGLE_TURN或ADAPTIVE_MULTI_TURN");
        strategyVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(strategyVersion, "strategyVersion"), 128, "strategyVersion");
        seedInput = ContractChecks.maxLength(
                ContractChecks.notBlankPreserving(seedInput, "seedInput"), 100_000, "seedInput");
        if (riskIndicators == null || riskIndicators.isEmpty()) {
            throw new IllegalArgumentException("riskIndicators不能为空，D02需要明确的疑似风险信号");
        }
        riskIndicators = riskIndicators.stream()
                .map(value -> ContractChecks.maxLength(
                        ContractChecks.notBlank(value, "riskIndicators项"), 256, "riskIndicators项"))
                .distinct()
                .toList();
        ContractChecks.require(riskIndicators.size() <= 20, "riskIndicators不能超过20项");
        budgetPolicy = ContractChecks.required(budgetPolicy, "budgetPolicy");
        ContractChecks.require(budgetPolicy.maxAttempts() == 1,
                "D02当前只支持一个AttackAttempt，maxAttempts必须为1");
        ContractChecks.require(budgetPolicy.maxConcurrentSessions() == 1,
                "D02当前只支持一个串行尝试，maxConcurrentSessions必须为1");
        ContractChecks.require(budgetPolicy.maxTokens() > 0,
                "D02会调用被测模型，maxTokens必须大于0");
        ContractChecks.require(budgetPolicy.maxCost() == null,
                "D02当前没有统一费用计量，不能配置maxCost");
    }
}
