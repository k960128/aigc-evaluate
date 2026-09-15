package com.kant.agent.persistence.memory;

import com.kant.agent.contract.RedTeamTaskResult;
import com.kant.agent.persistence.RedTeamTaskRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1 的内存仓储，用于验证任务结果回查和应用服务边界。
 *
 * <p>该实现只适合本地开发和链路验证：应用重启后数据会丢失，且没有分页、过期和持久化
 * 能力。M2 接入数据库或缓存时，只需要替换该接口实现。</p>
 */
@Repository
public class InMemoryRedTeamTaskRepository implements RedTeamTaskRepository {

    /** 使用并发 Map 保存任务结果，支持 Web 请求线程安全地读写。 */
    private final Map<String, RedTeamTaskResult> results = new ConcurrentHashMap<>();

    /**
     * 保存任务结果。
     *
     * @param result 待保存的任务结果
     * @return 原样返回传入结果，便于应用服务直接向 Controller 返回
     */
    @Override
    public RedTeamTaskResult save(RedTeamTaskResult result) {
        // 以 taskId 作为唯一键；同一任务再次保存时覆盖旧快照。
        results.put(result.taskId(), result);
        return result;
    }

    /**
     * 按任务 ID 查询内存中的结果。
     *
     * @param taskId 评测任务标识
     * @return 找到时返回任务结果，否则返回空 Optional
     */
    @Override
    public Optional<RedTeamTaskResult> findByTaskId(String taskId) {
        // ofNullable 将 Map 未命中的 null 安全地转换为 Optional.empty()。
        return Optional.ofNullable(results.get(taskId));
    }
}
