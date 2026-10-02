package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;

import java.util.List;

/**
 * 一次攻击决策所能看到的受限上下文。
 *
 * <p>编排器只把当前任务授权范围内的历史传给 Agent。Agent 没有机会自行
 * 指定其他租户、任务、目标或预算。</p>
 *
 * @param taskId              任务标识
 * @param runId               本次运行标识
 * @param attemptId           当前攻击尝试标识
 * @param targetSnapshotRef   固定目标快照引用
 * @param riskId              当前风险编码
 * @param strategyVersion     已授权策略版本
 * @param seedInput           固定策略的首轮种子
 * @param history             当前尝试已经完成的探测历史
 * @param remainingTargetCalls 剩余目标调用次数
 * @param remainingAgentCalls 剩余 Agent 调用次数
 * @param noProgressCount     连续无进展次数
 * @param riskObserved        代码侧是否已经观察到疑似风险信号
 */
public record AdaptiveAttackContext(
        String taskId,
        String runId,
        String attemptId,
        String targetSnapshotRef,
        String riskId,
        String strategyVersion,
        String seedInput,
        List<AttackHistoryItem> history,
        long remainingTargetCalls,
        long remainingAgentCalls,
        int noProgressCount,
        boolean riskObserved) {

    /** 冻结历史列表并校验预算值。 */
    public AdaptiveAttackContext {
        taskId = ContractChecks.maxLength(ContractChecks.notBlank(taskId, "taskId"), 64, "taskId");
        runId = ContractChecks.maxLength(ContractChecks.notBlank(runId, "runId"), 96, "runId");
        attemptId = ContractChecks.maxLength(ContractChecks.notBlank(attemptId, "attemptId"), 96, "attemptId");
        targetSnapshotRef = ContractChecks.maxLength(
                ContractChecks.notBlank(targetSnapshotRef, "targetSnapshotRef"), 160, "targetSnapshotRef");
        riskId = ContractChecks.maxLength(ContractChecks.notBlank(riskId, "riskId"), 128, "riskId");
        strategyVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(strategyVersion, "strategyVersion"), 128, "strategyVersion");
        seedInput = ContractChecks.maxLength(
                ContractChecks.notBlankPreserving(seedInput, "seedInput"), 100_000, "seedInput");
        history = history == null ? List.of() : List.copyOf(history);
        ContractChecks.nonNegative(remainingTargetCalls, "remainingTargetCalls");
        ContractChecks.nonNegative(remainingAgentCalls, "remainingAgentCalls");
        ContractChecks.nonNegative(noProgressCount, "noProgressCount");
    }
}
