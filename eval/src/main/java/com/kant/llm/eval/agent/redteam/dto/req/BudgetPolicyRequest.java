package com.kant.llm.eval.agent.redteam.dto.req;

import com.kant.agent.contract.budget.BudgetPolicy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * D02 任务预算请求。
 *
 * <p>所有字段都显式表达上限，不使用负数代表无限。D02 当前要求
 * {@code maxAttempts=1} 和 {@code maxConcurrentSessions=1}。</p>
 */
@Getter
@Setter
public class BudgetPolicyRequest {

    /** 被测目标最大调用次数。 */
    @NotNull
    @Positive
    private Long maxTargetCalls;

    /** 攻击 Agent 最大调用次数；规则 Agent 可以为0。 */
    @NotNull
    @PositiveOrZero
    private Long maxAgentCalls;

    /** 最大攻击尝试数，D02 必须为1。 */
    @NotNull
    @Positive
    private Long maxAttempts;

    /** 单会话最大轮数；D02 保留字段并要求为正数。 */
    @NotNull
    @Positive
    private Long maxTurnsPerSession;

    /** 所有受管模型调用的最大 Token 数；D02 使用统一估算值结算，必须大于0。 */
    @NotNull
    @Positive
    private Long maxTokens;

    /** 最大费用；当前 D02 目标适配器没有统一定价和计量，必须为空。 */
    private BigDecimal maxCost;

    /** 费用币种；当前 D02 与 maxCost 一样必须为空。 */
    private String currency;

    /** 从运行开始计算的最长秒数。 */
    @NotNull
    @Positive
    private Long maxDurationSeconds;

    /** 最大并发目标会话数，D02 必须为1。 */
    @NotNull
    @Positive
    private Long maxConcurrentSessions;

    /** 单个操作允许的最大重试次数。 */
    @NotNull
    @PositiveOrZero
    private Long maxRetriesPerOperation;

    /** 转换为领域预算对象，执行组合校验。 */
    public BudgetPolicy toDomain() {
        return new BudgetPolicy(
                maxTargetCalls,
                maxAgentCalls,
                maxAttempts,
                maxTurnsPerSession,
                maxTokens,
                maxCost,
                currency,
                maxDurationSeconds,
                maxConcurrentSessions,
                maxRetriesPerOperation);
    }
}
