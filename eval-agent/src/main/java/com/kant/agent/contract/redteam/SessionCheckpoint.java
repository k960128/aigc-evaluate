package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;

/**
 * 自动红队会话的追加式恢复检查点。
 *
 * <p>检查点只保存会话推进所需的引用和摘要，不复制输入、响应正文。恢复时
 * 通过 sessionId 查询 D01 的真实执行事实和证据，不能只依赖进程内内存。</p>
 *
 * @param checkpointId 检查点标识
 * @param sessionId 会话标识
 * @param taskId 所属任务
 * @param runId 所属运行
 * @param attemptId 所属尝试
 * @param version 会话内单调递增版本
 * @param turnCount 已完成轮数
 * @param nextSequence 下一条动作序号
 * @param lastProbeId 最近一次探测引用
 * @param lastExecutionId 最近一次物理执行引用
 * @param historyHash 已确认会话历史摘要
 * @param status 保存检查点时的会话状态
 * @param createdAt 创建时间
 */
public record SessionCheckpoint(
        String checkpointId,
        String sessionId,
        String taskId,
        String runId,
        String attemptId,
        long version,
        int turnCount,
        int nextSequence,
        String lastProbeId,
        String lastExecutionId,
        String historyHash,
        AttackSessionStatus status,
        Instant createdAt) {

    /** 校验检查点。 */
    public SessionCheckpoint {
        checkpointId = ContractChecks.maxLength(
                ContractChecks.notBlank(checkpointId, "checkpointId"), 96, "checkpointId");
        sessionId = ContractChecks.maxLength(
                ContractChecks.notBlank(sessionId, "sessionId"), 96, "sessionId");
        taskId = ContractChecks.maxLength(
                ContractChecks.notBlank(taskId, "taskId"), 96, "taskId");
        runId = ContractChecks.maxLength(
                ContractChecks.notBlank(runId, "runId"), 96, "runId");
        attemptId = ContractChecks.maxLength(
                ContractChecks.notBlank(attemptId, "attemptId"), 96, "attemptId");
        ContractChecks.nonNegative(version, "version");
        ContractChecks.nonNegative(turnCount, "turnCount");
        ContractChecks.positive(nextSequence, "nextSequence");
        historyHash = ContractChecks.maxLength(
                ContractChecks.notBlank(historyHash, "historyHash"), 128, "historyHash");
        status = ContractChecks.required(status, "status");
        createdAt = ContractChecks.required(createdAt, "createdAt");
    }
}
