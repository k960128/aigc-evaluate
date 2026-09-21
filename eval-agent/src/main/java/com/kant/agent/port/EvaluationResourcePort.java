package com.kant.agent.port;

import com.kant.agent.contract.common.AgentContext;
import com.kant.agent.contract.evaluation.EvaluationIntent;
import com.kant.agent.contract.evaluation.EvaluationResources;

/**
 * 获取现有评测业务中的资源事实。
 *
 * <p>M1 由 eval 模块提供适配实现，
 * eval-agent 不直接访问 eval 内部 Service 或 Mapper。</p>
 */
public interface EvaluationResourcePort {

    /**
     * 获取本次规划或提交校验需要的资源快照。
     *
     * <p>实现方必须：
     * 校验操作人权限；确认资源存在；
     * 提供真实样本数量和风险分类；
     * 计算稳定的数据集内容指纹。</p>
     */
    EvaluationResources load(
            EvaluationIntent intent,
            AgentContext context
    );
}