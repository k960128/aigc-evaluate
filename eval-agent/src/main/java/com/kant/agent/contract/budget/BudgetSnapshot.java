package com.kant.agent.contract.budget;

/**
 * 当前运行的预算消耗快照。
 *
 * @param targetCalls    已预留或已发生的目标调用次数
 * @param agentCalls     已预留或已发生的 Agent 调用次数
 * @param attempts       已创建的攻击尝试数
 * @param tokens         已结算或已保守记账的 Token 数；当前 D02 为估算值
 * @param reservedTokens 尚未完成结算的 Token 预留数；用于展示进程中断时的在途额度
 */
public record BudgetSnapshot(long targetCalls,
                             long agentCalls,
                             long attempts,
                             long tokens,
                             long reservedTokens) {

    /** 校验账本快照不能出现负数。 */
    public BudgetSnapshot {
        if (targetCalls < 0 || agentCalls < 0 || attempts < 0
                || tokens < 0 || reservedTokens < 0) {
            throw new IllegalArgumentException("预算消耗快照不能包含负数");
        }
    }
}
