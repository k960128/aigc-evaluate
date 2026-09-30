package com.kant.llm.eval.agent.redteam.dto.resp;

import com.kant.agent.contract.budget.BudgetSnapshot;

/**
 * 当前运行的预算消耗快照。
 *
 * @param targetCalls    已预留或已发生的目标调用次数
 * @param agentCalls     已预留或已发生的 Agent 调用次数
 * @param attempts       已创建的攻击尝试次数
 * @param tokens         已结算或保守记账的 Token 数；当前 D02 为估算值
 * @param reservedTokens 尚未完成结算的 Token 预留数
 */
public record BudgetSnapshotResponse(long targetCalls,
                                     long agentCalls,
                                     long attempts,
                                     long tokens,
                                     long reservedTokens) {

    /** 将领域预算快照转换为 API 响应。 */
    public static BudgetSnapshotResponse from(BudgetSnapshot snapshot) {
        return new BudgetSnapshotResponse(
                snapshot.targetCalls(),
                snapshot.agentCalls(),
                snapshot.attempts(),
                snapshot.tokens(),
                snapshot.reservedTokens());
    }
}
