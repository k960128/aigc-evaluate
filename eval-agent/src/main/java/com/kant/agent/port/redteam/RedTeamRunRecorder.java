package com.kant.agent.port.redteam;

import com.kant.agent.contract.redteam.RedTeamActionRecord;
import com.kant.agent.contract.redteam.RedTeamAttempt;
import com.kant.agent.contract.redteam.RedTeamRun;
import com.kant.agent.contract.redteam.SuspectedFinding;

import java.util.List;
import java.util.Optional;

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
}
