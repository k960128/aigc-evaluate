package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 攻击 Agent 的一次决策结果。
 *
 * <p>将生成失败作为独立状态返回，避免把攻击模型拒绝生成误记成被测目标
 * 的安全拒答。</p>
 *
 * @param status       决策状态
 * @param action       已生成的动作；生成失败时为空
 * @param errorCode    Agent 生成错误编码
 * @param errorMessage 可安全记录的生成错误摘要
 * @param agentCalls   本次决策实际消耗的 Agent 调用次数
 */
public record AttackDecision(
        AttackDecisionStatus status,
        AttackAction action,
        String errorCode,
        String errorMessage,
        long agentCalls) {

    /** 校验决策状态和消耗。 */
    public AttackDecision {
        status = ContractChecks.required(status, "status");
        errorCode = ContractChecks.maxLength(ContractChecks.nullableText(errorCode), 64, "errorCode");
        errorMessage = ContractChecks.maxLength(
                ContractChecks.nullableText(errorMessage), 1_000, "errorMessage");
        ContractChecks.nonNegative(agentCalls, "agentCalls");
        if (status == AttackDecisionStatus.GENERATION_FAILED) {
            ContractChecks.require(action == null, "生成失败时不能携带action");
            ContractChecks.notBlank(errorCode, "generationFailed.errorCode");
            ContractChecks.notBlank(errorMessage, "generationFailed.errorMessage");
        } else {
            ContractChecks.required(action, "action");
            if (status == AttackDecisionStatus.ACTION_PROPOSED) {
                ContractChecks.require(action.actionType() == AttackActionType.SEND_INPUT,
                        "ACTION_PROPOSED只能携带SEND_INPUT动作");
            }
            if (status == AttackDecisionStatus.END_REQUESTED) {
                ContractChecks.require(action.actionType() == AttackActionType.END_ATTEMPT,
                        "END_REQUESTED只能携带END_ATTEMPT动作");
            }
        }
    }

    /** 创建普通动作结果。 */
    public static AttackDecision action(AttackAction action, long agentCalls) {
        return new AttackDecision(
                action.actionType() == AttackActionType.END_ATTEMPT
                        ? AttackDecisionStatus.END_REQUESTED
                        : AttackDecisionStatus.ACTION_PROPOSED,
                action,
                null,
                null,
                agentCalls);
    }

    /** 创建生成失败结果。 */
    public static AttackDecision failure(String errorCode, String errorMessage, long agentCalls) {
        return new AttackDecision(
                AttackDecisionStatus.GENERATION_FAILED,
                null,
                errorCode,
                errorMessage,
                agentCalls);
    }
}
