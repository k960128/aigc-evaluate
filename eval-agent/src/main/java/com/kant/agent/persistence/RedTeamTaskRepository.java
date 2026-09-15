package com.kant.agent.persistence;

import com.kant.agent.contract.RedTeamTaskResult;

import java.util.Optional;

/**
 * 红队任务结果仓储边界。
 *
 * <p>M1 使用内存实现，M2 再替换为 MySQL/Redis 实现。应用服务只依赖该接口，
 * 因此替换存储介质时不需要修改编排流程。</p>
 */
public interface RedTeamTaskRepository {

    /**
     * 保存一次评测任务结果。
     *
     * @param result 待保存的任务结果
     * @return 已保存的任务结果
     */
    RedTeamTaskResult save(RedTeamTaskResult result);

    /**
     * 按任务标识查询评测结果。
     *
     * @param taskId 评测任务标识
     * @return 找到结果时返回 Optional，否则返回空 Optional
     */
    Optional<RedTeamTaskResult> findByTaskId(String taskId);
}
