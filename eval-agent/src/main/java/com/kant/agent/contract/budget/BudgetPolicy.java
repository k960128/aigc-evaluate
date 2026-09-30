package com.kant.agent.contract.budget;

import com.kant.agent.contract.common.ContractChecks;

import java.math.BigDecimal;

/**
 * 一次评测运行的不可变预算上限。
 *
 * <p>D01 锁定字段语义；D02 对目标调用次数、攻击调用次数、尝试次数和估算
 * Token 执行单运行账本。费用字段仍需目标适配器提供统一定价和计量后才能强制。</p>
 *
 * @param maxTargetCalls          被测目标最大调用次数
 * @param maxAgentCalls           规划、攻击、判定等 Agent 模型最大调用次数
 * @param maxAttempts             最大攻击尝试数
 * @param maxTurnsPerSession      单个目标会话最大输入轮数
 * @param maxTokens               全部受管模型调用的最大 Token 数
 * @param maxCost                 最大费用；无法可靠计费时必须为空
 * @param currency                费用币种；设置费用时必填
 * @param maxDurationSeconds      最大运行秒数
 * @param maxConcurrentSessions   最大并发目标会话数
 * @param maxRetriesPerOperation  单个业务操作最大重试次数
 */
public record BudgetPolicy(
        long maxTargetCalls,
        long maxAgentCalls,
        long maxAttempts,
        long maxTurnsPerSession,
        long maxTokens,
        BigDecimal maxCost,
        String currency,
        long maxDurationSeconds,
        long maxConcurrentSessions,
        long maxRetriesPerOperation) {

    /**
     * 校验预算值及费用和币种的组合关系。
     */
    public BudgetPolicy {
        ContractChecks.positive(maxTargetCalls, "maxTargetCalls");
        ContractChecks.nonNegative(maxAgentCalls, "maxAgentCalls");
        ContractChecks.positive(maxAttempts, "maxAttempts");
        ContractChecks.positive(maxTurnsPerSession, "maxTurnsPerSession");
        ContractChecks.nonNegative(maxTokens, "maxTokens");
        ContractChecks.positive(maxDurationSeconds, "maxDurationSeconds");
        ContractChecks.positive(maxConcurrentSessions, "maxConcurrentSessions");
        ContractChecks.nonNegative(maxRetriesPerOperation, "maxRetriesPerOperation");
        if (maxCost != null) {
            ContractChecks.require(maxCost.signum() >= 0, "maxCost不能小于0");
            currency = ContractChecks.maxLength(
                    ContractChecks.notBlank(currency, "currency"), 16, "currency");
        } else {
            ContractChecks.require(currency == null || currency.isBlank(),
                    "未设置maxCost时不能单独设置currency");
            currency = null;
        }
    }
}
