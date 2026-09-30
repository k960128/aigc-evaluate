package com.kant.llm.eval.agent.redteam.dto.resp;

import com.kant.agent.contract.redteam.RedTeamTask;

import java.time.Instant;
import java.util.List;

/**
 * 自动红队任务响应。
 *
 * @param taskId             任务标识
 * @param activityId         评测活动标识
 * @param targetType         目标类型
 * @param targetId           目标资源标识
 * @param targetSnapshotRef  创建任务时固定的目标快照
 * @param riskId             风险编码
 * @param ruleSetVersion     规则版本
 * @param executionMode      任务层执行模式
 * @param strategyVersion    固定策略版本
 * @param seedInput          首轮种子输入
 * @param riskIndicators     代码侧疑似风险信号
 * @param budgetPolicy       预算上限
 * @param status             任务状态
 * @param stopReason         最近一次运行停止原因
 * @param stopMessage        最近一次运行停止摘要
 * @param createdAt          创建时间
 * @param startedAt          最近一次启动时间
 * @param finishedAt         最近一次结束时间
 */
public record RedTeamTaskResponse(
        String taskId,
        String activityId,
        String targetType,
        String targetId,
        String targetSnapshotRef,
        String riskId,
        String ruleSetVersion,
        String executionMode,
        String strategyVersion,
        String seedInput,
        List<String> riskIndicators,
        BudgetPolicyResponse budgetPolicy,
        String status,
        String stopReason,
        String stopMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    /** 将领域任务转换为 API 响应。 */
    public static RedTeamTaskResponse from(RedTeamTask task) {
        return new RedTeamTaskResponse(
                task.taskId(),
                task.activityId(),
                task.target().targetType().name(),
                task.target().targetId(),
                task.targetSnapshotRef(),
                task.riskId(),
                task.ruleSetVersion(),
                task.executionMode().name(),
                task.strategyVersion(),
                task.seedInput(),
                task.riskIndicators(),
                BudgetPolicyResponse.from(task.budgetPolicy()),
                task.status().name(),
                task.stopReason() == null ? null : task.stopReason().name(),
                task.stopMessage(),
                task.createdAt(),
                task.startedAt(),
                task.finishedAt());
    }
}
