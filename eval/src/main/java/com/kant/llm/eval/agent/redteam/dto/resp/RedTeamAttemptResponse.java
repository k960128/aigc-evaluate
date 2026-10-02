package com.kant.llm.eval.agent.redteam.dto.resp;

import com.kant.agent.contract.redteam.RedTeamAttempt;

import java.time.Instant;

/**
 * 自动红队攻击尝试响应。
 *
 * <p>尝试是一个风险与策略组合下的探索单元。D02 当前每个运行只有一个
 * 尝试，但仍然通过独立字段返回，避免前端把动作数量误当成尝试数量，
 * 也为后续多风险轮换保留稳定的响应形状。</p>
 *
 * @param attemptId 尝试标识
 * @param taskId 所属任务标识
 * @param runId 所属运行标识
 * @param sequence 运行内尝试序号
 * @param riskId 本次尝试验证的风险编码
 * @param strategyVersion 本次尝试固定使用的策略版本
 * @param status 尝试状态
 * @param probeCount 已被 D01 受理并形成观察的探测数量
 * @param stopReason 尝试停止原因
 * @param stopMessage 尝试停止摘要
 * @param startedAt 尝试开始时间
 * @param finishedAt 尝试结束时间
 */
public record RedTeamAttemptResponse(
        String attemptId,
        String taskId,
        String runId,
        int sequence,
        String riskId,
        String strategyVersion,
        String status,
        int probeCount,
        String stopReason,
        String stopMessage,
        Instant startedAt,
        Instant finishedAt) {

    /** 将领域攻击尝试转换为 API 响应。 */
    public static RedTeamAttemptResponse from(RedTeamAttempt attempt) {
        return new RedTeamAttemptResponse(
                attempt.attemptId(),
                attempt.taskId(),
                attempt.runId(),
                attempt.sequence(),
                attempt.riskId(),
                attempt.strategyVersion(),
                attempt.status().name(),
                attempt.probeCount(),
                attempt.stopReason() == null ? null : attempt.stopReason().name(),
                attempt.stopMessage(),
                attempt.startedAt(),
                attempt.finishedAt());
    }
}
