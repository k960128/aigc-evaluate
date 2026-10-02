package com.kant.llm.eval.agent.redteam.dto.resp;

import com.kant.agent.contract.redteam.RedTeamActionRecord;
import com.kant.agent.contract.redteam.RedTeamAttempt;
import com.kant.agent.contract.redteam.RedTeamRun;
import com.kant.agent.contract.redteam.SuspectedFinding;

import java.time.Instant;
import java.util.List;

/**
 * 自动红队运行响应。
 *
 * @param runId          运行标识
 * @param taskId         所属任务
 * @param status         运行状态
 * @param budget         当前消耗快照
 * @param actionCount    已记录动作数
 * @param currentAttemptId 当前攻击尝试
 * @param stopReason     停止原因
 * @param stopMessage    停止摘要
 * @param stopRequested  是否收到用户停止意图
 * @param actions        动作轨迹
 * @param suspectedFindings 疑似发现列表
 * @param attempts       攻击尝试摘要列表；D02 当前通常只有一项
 * @param session        D03 当前目标会话摘要；D02 单轮运行为空
 */
public record RedTeamRunResponse(
        String runId,
        String taskId,
        String status,
        BudgetSnapshotResponse budget,
        int actionCount,
        String currentAttemptId,
        String stopReason,
        String stopMessage,
        boolean stopRequested,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        List<RedTeamActionResponse> actions,
        List<SuspectedFindingResponse> suspectedFindings,
        List<RedTeamAttemptResponse> attempts,
        RedTeamSessionResponse session) {

    /** 将运行摘要和明细转换为 API 响应。 */
    public static RedTeamRunResponse from(RedTeamRun run,
                                          List<RedTeamActionRecord> actions,
                                          List<SuspectedFinding> findings,
                                          List<RedTeamAttempt> attempts) {
        return from(run, actions, findings, attempts, null);
    }

    /** 将运行及其可选的 D03 会话摘要转换为 API 响应。 */
    public static RedTeamRunResponse from(RedTeamRun run,
                                          List<RedTeamActionRecord> actions,
                                          List<SuspectedFinding> findings,
                                          List<RedTeamAttempt> attempts,
                                          com.kant.agent.contract.redteam.AttackSession session) {
        return new RedTeamRunResponse(
                run.runId(),
                run.taskId(),
                run.status().name(),
                BudgetSnapshotResponse.from(run.budget()),
                run.actionCount(),
                run.currentAttemptId(),
                run.stopReason() == null ? null : run.stopReason().name(),
                run.stopMessage(),
                run.stopRequested(),
                run.createdAt(),
                run.startedAt(),
                run.finishedAt(),
                actions == null ? List.of() : actions.stream().map(RedTeamActionResponse::from).toList(),
                findings == null ? List.of() : findings.stream().map(SuspectedFindingResponse::from).toList(),
                attempts == null ? List.of() : attempts.stream().map(RedTeamAttemptResponse::from).toList(),
                RedTeamSessionResponse.from(session));
    }
}
