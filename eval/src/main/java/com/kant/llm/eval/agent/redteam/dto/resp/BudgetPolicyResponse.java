package com.kant.llm.eval.agent.redteam.dto.resp;

import com.kant.agent.contract.budget.BudgetPolicy;

import java.math.BigDecimal;

/**
 * 任务预算配置响应。
 *
 * <p>调用次数和当前 D02 的估算 Token 上限会由代码强制；费用上限在当前适配器
 * 不可计量时返回为空，前端不应把空值展示成零费用。</p>
 */
public record BudgetPolicyResponse(
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

    /** 将领域预算转换为 API 响应。 */
    public static BudgetPolicyResponse from(BudgetPolicy policy) {
        return new BudgetPolicyResponse(
                policy.maxTargetCalls(),
                policy.maxAgentCalls(),
                policy.maxAttempts(),
                policy.maxTurnsPerSession(),
                policy.maxTokens(),
                policy.maxCost(),
                policy.currency(),
                policy.maxDurationSeconds(),
                policy.maxConcurrentSessions(),
                policy.maxRetriesPerOperation());
    }
}
