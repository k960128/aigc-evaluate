package com.kant.agent.contract.production;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 独立生产额度，不使用要求目标调用次数为正的探测BudgetPolicy。
 * @param maxAgentCalls 生成与质量改写调用总上限
 * @param maxTokens 总Token预算；调用前预留输入估算与输出上限，仍需标明计量局限
 * @param maxDurationSeconds 本批固定运行期限，恢复不延长
 */
public record ProductionBudget(long maxAgentCalls, long maxTokens, long maxDurationSeconds) {
    public ProductionBudget {
        ContractChecks.positive(maxAgentCalls, "maxAgentCalls");
        ContractChecks.positive(maxTokens, "maxTokens");
        ContractChecks.require(maxDurationSeconds >= 1 && maxDurationSeconds <= 86400, "生产运行期限为1至86400秒");
    }

    /** agentCalls包含在途预留；reservedTokens不能因重启或UNKNOWN而丢失。 */
    public record Usage(long agentCalls, long tokens, long reservedTokens) {
        public Usage {
            ContractChecks.nonNegative(agentCalls, "agentCalls");
            ContractChecks.nonNegative(tokens, "tokens");
            ContractChecks.nonNegative(reservedTokens, "reservedTokens");
        }
    }
}
