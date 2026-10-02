package com.kant.agent.port.redteam;

import com.kant.agent.contract.redteam.AdaptiveAttackContext;
import com.kant.agent.contract.redteam.AttackDecision;

/**
 * 自适应攻击决策端口。
 *
 * <p>实现可以是规则策略、Spring AI ReAct 或其他模型适配器，但只能返回
 * 结构化 {@link AttackDecision}。目标调用、预算扣减和状态持久化不属于 Agent。</p>
 */
public interface AdaptiveAttackAgent {

    /**
     * 返回执行一次决策前需要预留的 Agent 调用上限。
     *
     * <p>该方法不能调用模型，只能根据实现配置和当前上下文返回确定的上限。
     * 编排器会在调用 {@link #decide(AdaptiveAttackContext)} 前完成预算预留，
     * 这样模型调用不会发生在预算校验之后。规则 Agent 返回 0；Spring AI
     * 实现通常按一次结构化模型调用返回 1。</p>
     *
     * @param context 经编排器裁剪和授权后的上下文
     * @return 本次决策最多消耗的 Agent 调用次数
     */
    long plannedAgentCalls(AdaptiveAttackContext context);

    /**
     * 根据当前尝试历史提出下一步动作。
     *
     * @param context 经编排器裁剪和授权后的上下文
     * @return 结构化攻击决策
     */
    AttackDecision decide(AdaptiveAttackContext context);
}
