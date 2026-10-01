package com.kant.agent.port.redteam;

import com.kant.agent.contract.redteam.RedTeamActionRecord;
import com.kant.agent.contract.redteam.AttackSession;
import com.kant.agent.contract.redteam.RedTeamAttempt;
import com.kant.agent.contract.redteam.RedTeamRun;
import com.kant.agent.contract.redteam.SessionCheckpoint;
import com.kant.agent.contract.redteam.SuspectedFinding;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * 自动红队运行、尝试、动作和疑似发现的事实记录端口。
 *
 * <p>运行事实由数据库保存，不能依赖 Agent 进程内的聊天历史作为唯一状态。</p>
 */
public interface RedTeamRunRecorder {

    /** 创建一条运行记录；具体初始状态由调用方传入。 */
    void createRun(RedTeamRun run);

    /** 按当前身份读取运行摘要。 */
    Optional<RedTeamRun> findRun(String tenantId, String actorId, String runId);

    /** 保存运行状态和预算快照。 */
    void updateRun(RedTeamRun run);

    /** 创建或更新一次攻击尝试。 */
    void saveAttempt(RedTeamAttempt attempt);

    /** 保存一条动作审计记录。 */
    void saveAction(RedTeamActionRecord action);

    /** 保存一条疑似发现。 */
    void saveFinding(SuspectedFinding finding);

    /** 读取当前运行已经保存的动作。 */
    List<RedTeamActionRecord> listActions(String tenantId, String actorId, String runId);

    /** 读取当前运行已经保存的攻击尝试。 */
    List<RedTeamAttempt> listAttempts(String tenantId, String actorId, String runId);

    /** 读取当前运行已经产生的疑似发现。 */
    List<SuspectedFinding> listFindings(String tenantId, String actorId, String runId);

    /** 记录用户停止意图，循环会在下一次动作边界检查它。 */
    void requestStop(String tenantId, String actorId, String runId);

    /** 查询是否存在用户停止意图。 */
    boolean isStopRequested(String tenantId, String actorId, String runId);

    /** 创建一次目标会话的可恢复控制面记录。 */
    void createSession(AttackSession session);

    /** 按身份读取目标会话。 */
    Optional<AttackSession> findSession(String tenantId, String actorId, String sessionId);

    /** 按身份读取运行当前会话。 */
    Optional<AttackSession> findSessionByRun(String tenantId, String actorId, String runId);

    /** 更新会话状态和最新检查点摘要。 */
    void updateSession(AttackSession session);

    /** 保存追加式会话检查点。 */
    void saveCheckpoint(SessionCheckpoint checkpoint);

    /** 读取会话最后一个检查点。 */
    Optional<SessionCheckpoint> findLatestCheckpoint(
            String tenantId,
            String actorId,
            String sessionId);

    /** 记录暂停意图，会话在动作边界进入可恢复检查点。 */
    void requestPause(String tenantId, String actorId, String runId);

    /** 清除暂停意图并允许后台恢复会话。 */
    void requestResume(String tenantId, String actorId, String runId);

    /** 查询当前运行是否收到暂停意图。 */
    boolean isPauseRequested(String tenantId, String actorId, String runId);

    /** 查询需要由后台 Worker 接管的多轮运行。 */
    List<RedTeamRun> listRecoverableRuns();

    /** 以数据库租约抢占一条运行，避免多个 Worker 同时推进同一会话。 */
    boolean tryClaimWorker(RedTeamRun run, String workerId, Instant leaseUntil);

    /** 续租当前 Worker 持有的运行；续租失败表示已失去推进资格。 */
    boolean renewWorkerLease(RedTeamRun run, String workerId, Instant leaseUntil);

    /** 释放当前 Worker 持有的运行租约。 */
    void releaseWorkerLease(RedTeamRun run, String workerId);

    /** 在租约栅栏保护下原子保存动作、预算、检查点和终态；空的可选对象表示不修改。 */
    void saveProgress(String workerId, RedTeamRun run, AttackSession session,
                      SessionCheckpoint checkpoint, RedTeamAttempt attempt,
                      RedTeamActionRecord action, SuspectedFinding finding,
                      com.kant.agent.contract.redteam.PendingProbe operation);

    /** 读取唯一的未结算目标操作，恢复不能重新生成其输入。 */
    Optional<com.kant.agent.contract.redteam.PendingProbe> findPendingProbe(
            String tenantId, String actorId, String runId);
}
