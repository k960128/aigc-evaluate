package com.kant.agent.port.redteam;

import com.kant.agent.contract.redteam.RedTeamTask;

import java.util.Optional;

/**
 * 自动红队任务配置的持久化端口。
 */
public interface RedTeamTaskRepository {

    /** 保存一份不可变任务配置。 */
    void create(RedTeamTask task);

    /** 按当前身份读取任务，避免跨租户或跨操作者读取配置。 */
    Optional<RedTeamTask> find(String tenantId, String actorId, String taskId);

    /** 按租户和幂等键读取已有任务。 */
    Optional<RedTeamTask> findByIdempotencyKey(String tenantId, String idempotencyKey);

    /** 更新任务的汇总状态，不修改已经固定的配置字段。 */
    void updateStatus(String tenantId,
                      String actorId,
                      String taskId,
                      RedTeamTask task);
}
