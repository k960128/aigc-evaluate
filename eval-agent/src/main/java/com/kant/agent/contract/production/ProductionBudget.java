package com.kant.agent.contract.production;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 独立生产额度，不使用要求目标调用次数为正的探测BudgetPolicy。
 * @param maxAgentCalls 生成与质量改写调用总上限
 * @param maxTokens 单批总Token预算；调用前预留输入估算与输出上限，仍需标明计量局限
 * @param maxDurationSeconds 从受理起算的固定期限，包含排队，范围1至86400秒，恢复不延长
 */
public record ProductionBudget(long maxAgentCalls, long maxTokens, long maxDurationSeconds) {
    /** 校验预算自身不变量；调用次数能否覆盖根槽位由计划服务进一步检查。 */
    public ProductionBudget {
        ContractChecks.positive(maxAgentCalls, "maxAgentCalls");
        ContractChecks.positive(maxTokens, "maxTokens");
        ContractChecks.require(maxDurationSeconds >= 1 && maxDurationSeconds <= 86400, "生产运行期限为1至86400秒");
    }

    /**
     * 批次独立账本，预留不能因重启、UNKNOWN、停止或超期而直接归零。
     * @param agentCalls 已受理生成/改写次数，含在途；明确未发送的取消可退还
     * @param tokens 已确认响应累计输入输出Token，可能含估算，不是精确金额
     * @param reservedTokens 尚未确认或尚未结算操作的Token预留
     */
    public record Usage(long agentCalls, long tokens, long reservedTokens) {
        /** 禁止负账本；结算与释放预留必须在业务事务中维护一致性。 */
        public Usage {
            ContractChecks.nonNegative(agentCalls, "agentCalls");
            ContractChecks.nonNegative(tokens, "tokens");
            ContractChecks.nonNegative(reservedTokens, "reservedTokens");
        }
    }
}
