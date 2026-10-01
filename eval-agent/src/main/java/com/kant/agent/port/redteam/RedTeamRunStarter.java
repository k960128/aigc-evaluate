package com.kant.agent.port.redteam;

import com.kant.agent.contract.redteam.RedTeamRun;
import com.kant.agent.contract.redteam.RedTeamTask;

/**
 * 自动红队运行启动协调端口。
 *
 * <p>启动不是简单的“先查状态再更新状态”。多个请求可能同时读取到同一份
 * 可启动任务，因此需要由持久化适配器在一个短事务中条件抢占任务并创建运行。
 * 只有抢占成功的一方才可以继续调用目标。</p>
 */
public interface RedTeamRunStarter {

    /**
     * 条件抢占任务并原子创建一个活动运行。
     *
     * @param expectedTask 调用方读取到的任务快照，状态是条件的一部分
     * @param runningTask  抢占成功后要写入的任务状态
     * @param runningRun   抢占成功后要创建的运行记录
     * @return 当前任务仍匹配快照并完成创建时返回 {@code true}
     */
    boolean tryCreateAndClaim(RedTeamTask expectedTask,
                              RedTeamTask runningTask,
                              RedTeamRun runningRun);
}
