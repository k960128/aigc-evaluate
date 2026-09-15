package com.kant.agent.application;

import com.kant.agent.contract.RedTeamEvaluateRequest;
import com.kant.agent.contract.RedTeamTaskResult;
import com.kant.agent.orchestration.RedTeamOrchestrator;
import com.kant.agent.persistence.RedTeamTaskRepository;
import org.springframework.stereotype.Service;

/**
 * 红队评测应用服务，负责承接 Controller 与编排器之间的用例边界。
 *
 * <p>应用服务负责组合“执行用例”和“保存结果”两个应用层动作。它不参与样本生成、
 * 模型调用或 Judge 规则，从而让 Controller 保持轻量，也为后续异步任务入口预留统一边界。</p>
 */
@Service
public class RedTeamEvaluateApplicationService {

    /** 负责按业务顺序执行一次评测的编排器。 */
    private final RedTeamOrchestrator orchestrator;

    /** 负责保存和查询任务结果的仓储。 */
    private final RedTeamTaskRepository taskRepository;

    /**
     * 创建红队评测应用服务。
     *
     * @param orchestrator 红队评测编排器
     * @param taskRepository 任务结果仓储
     */
    public RedTeamEvaluateApplicationService(RedTeamOrchestrator orchestrator,
                                             RedTeamTaskRepository taskRepository) {
        this.orchestrator = orchestrator;
        this.taskRepository = taskRepository;
    }

    /**
     * 执行并保存一次同步红队评测。
     *
     * @param request 评测请求
     * @return 已保存的评测任务结果
     */
    public RedTeamTaskResult evaluate(RedTeamEvaluateRequest request) {
        // M1 先同步执行，再统一保存最终快照；M2 可以在这里切换为提交异步任务。
        return taskRepository.save(orchestrator.execute(request));
    }

    /**
     * 根据任务 ID 查询评测结果。
     *
     * @param taskId 评测任务标识
     * @return 找到任务时返回结果，未找到时返回 null 以保持当前接口契约
     */
    public RedTeamTaskResult findByTaskId(String taskId) {
        // 仓储使用 Optional 表达查询语义，应用层在 M1 将未找到结果转换为空值。
        return taskRepository.findByTaskId(taskId).orElse(null);
    }
}
