package com.kant.agent.agent;

import com.kant.agent.contract.AttackSample;
import com.kant.agent.contract.RedTeamEvaluateRequest;

/**
 * 样本智能体业务边界。
 *
 * <p>样本智能体负责把评测请求转换为可发送给目标模型的攻击样本。接口只暴露领域
 * 对象，使编排器不需要关心样本来自固定种子、模型生成还是后续的变异工厂。</p>
 */
public interface SampleAgent {

    /**
     * 为指定评测任务创建一个攻击样本。
     *
     * @param taskId 当前评测任务标识
     * @param request 创建评测任务时提交的业务请求
     * @return 可以直接发送给目标模型的攻击样本
     */
    AttackSample create(String taskId, RedTeamEvaluateRequest request);
}
