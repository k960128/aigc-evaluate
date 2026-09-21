package com.kant.agent.agent;

import com.kant.agent.contract.common.AgentContext;
import com.kant.agent.contract.common.AgentResult;
import com.kant.agent.contract.evaluation.EvaluationPlanDraft;
import com.kant.agent.contract.evaluation.PlanningInput;

/**
 * 评测主智能体。
 *
 * <p>只提出结构化计划，不负责审批、状态修改或任务提交。</p>
 */
public interface EvaluationSupervisorAgent {

    /**
     * 根据意图、资源和可选反馈生成计划草案。
     */
    AgentResult<EvaluationPlanDraft> proposePlan(
            PlanningInput input,
            AgentContext context
    );
}