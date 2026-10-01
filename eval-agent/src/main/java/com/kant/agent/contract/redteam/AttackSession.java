package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;

/**
 * 一次攻击尝试中的目标会话编排状态。
 *
 * <p>该对象是可恢复控制面的摘要。目标响应正文仍由 D01 证据表保存，
 * 当前会话只保存最后检查点和执行引用。</p>
 *
 * @param sessionId 平台会话标识，同时作为 D01 的 sessionRef
 * @param taskId 所属任务
 * @param runId 所属运行
 * @param attemptId 所属攻击尝试
 * @param tenantId 业务空间
 * @param actorId 发起操作者
 * @param targetSnapshotRef 会话绑定的目标快照
 * @param status 会话状态
 * @param turnCount 已完成的目标消息轮数
 * @param nextSequence 下一条动作序号
 * @param lastProbeId 最近一次已确认的逻辑探测
 * @param lastExecutionId 最近一次已确认的物理执行
 * @param pauseRequested 是否收到暂停意图
 * @param stopRequested 是否收到停止意图
 * @param checkpointVersion 最新检查点版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param closedAt 关闭时间
 * @param errorCode 可解释的会话错误编码
 * @param errorMessage 可安全展示的错误摘要
 */
public record AttackSession(
        String sessionId,
        String taskId,
        String runId,
        String attemptId,
        String tenantId,
        String actorId,
        String targetSnapshotRef,
        AttackSessionStatus status,
        int turnCount,
        int nextSequence,
        String lastProbeId,
        String lastExecutionId,
        boolean pauseRequested,
        boolean stopRequested,
        long checkpointVersion,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        String errorCode,
        String errorMessage) {

    /** 校验会话摘要。 */
    public AttackSession {
        sessionId = ContractChecks.maxLength(
                ContractChecks.notBlank(sessionId, "sessionId"), 96, "sessionId");
        taskId = ContractChecks.maxLength(
                ContractChecks.notBlank(taskId, "taskId"), 96, "taskId");
        runId = ContractChecks.maxLength(
                ContractChecks.notBlank(runId, "runId"), 96, "runId");
        attemptId = ContractChecks.maxLength(
                ContractChecks.notBlank(attemptId, "attemptId"), 96, "attemptId");
        tenantId = ContractChecks.maxLength(
                ContractChecks.notBlank(tenantId, "tenantId"), 64, "tenantId");
        actorId = ContractChecks.maxLength(
                ContractChecks.notBlank(actorId, "actorId"), 64, "actorId");
        targetSnapshotRef = ContractChecks.maxLength(
                ContractChecks.notBlank(targetSnapshotRef, "targetSnapshotRef"), 160,
                "targetSnapshotRef");
        status = ContractChecks.required(status, "status");
        ContractChecks.nonNegative(turnCount, "turnCount");
        ContractChecks.positive(nextSequence, "nextSequence");
        ContractChecks.nonNegative(checkpointVersion, "checkpointVersion");
        createdAt = ContractChecks.required(createdAt, "createdAt");
        updatedAt = ContractChecks.required(updatedAt, "updatedAt");
        errorCode = ContractChecks.maxLength(
                ContractChecks.nullableText(errorCode), 64, "errorCode");
        errorMessage = ContractChecks.maxLength(
                ContractChecks.nullableText(errorMessage), 1_000, "errorMessage");
    }
}
