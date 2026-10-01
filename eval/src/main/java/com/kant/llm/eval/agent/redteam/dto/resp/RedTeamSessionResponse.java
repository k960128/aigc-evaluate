package com.kant.llm.eval.agent.redteam.dto.resp;

import com.kant.agent.contract.redteam.AttackSession;

import java.time.Instant;

/**
 * D03 目标会话摘要。
 *
 * <p>响应只返回控制面信息和引用，不返回目标输入、响应正文或敏感证据正文；
 * 前端应通过证据授权接口按需查看脱敏内容。</p>
 *
 * @param sessionId 会话标识，也是多轮探测使用的 sessionRef
 * @param status 会话状态，例如 ACTIVE、PAUSED、BLOCKED、COMPLETED
 * @param turnCount 已确认的 user/assistant 交互轮数
 * @param nextSequence 下一条攻击动作序号
 * @param lastProbeId 最近一次已确认的逻辑探测标识
 * @param lastExecutionId 最近一次已确认的物理执行标识
 * @param checkpointVersion 最新检查点版本
 * @param pauseRequested 是否已收到暂停命令
 * @param stopRequested 是否已收到停止命令
 * @param targetSnapshotRef 会话绑定的目标快照
 * @param errorCode 会话阻塞或结束时的稳定错误编码
 * @param errorMessage 可安全展示的状态摘要
 * @param createdAt 会话创建时间
 * @param updatedAt 会话最近更新时间
 * @param closedAt 会话关闭时间；未关闭时为空
 */
public record RedTeamSessionResponse(
        String sessionId,
        String status,
        int turnCount,
        int nextSequence,
        String lastProbeId,
        String lastExecutionId,
        long checkpointVersion,
        boolean pauseRequested,
        boolean stopRequested,
        String targetSnapshotRef,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {

    /** 将领域会话转换为不含正文的 API 摘要。 */
    public static RedTeamSessionResponse from(AttackSession session) {
        if (session == null) {
            return null;
        }
        return new RedTeamSessionResponse(
                session.sessionId(),
                session.status().name(),
                session.turnCount(),
                session.nextSequence(),
                session.lastProbeId(),
                session.lastExecutionId(),
                session.checkpointVersion(),
                session.pauseRequested(),
                session.stopRequested(),
                session.targetSnapshotRef(),
                session.errorCode(),
                session.errorMessage(),
                session.createdAt(),
                session.updatedAt(),
                session.closedAt());
    }
}
