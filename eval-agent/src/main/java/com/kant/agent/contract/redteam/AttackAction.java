package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;

import java.util.List;

/**
 * 攻击 Agent 提议的一步动作。
 *
 * <p>这是建议，不是执行授权。动作必须先经过 {@code AttackActionValidator}
 * 和编排器的预算、能力及授权校验。</p>
 *
 * @param actionType          动作类型
 * @param input               SEND_INPUT 时实际要提交的文本
 * @param parentProbeRef      本次输入的父探测引用；首轮可以为空
 * @param strategyVersion     Agent 使用的策略版本
 * @param reasonSummary       面向审计的简短依据，不保存内部思维链
 * @param observationRefs     支撑本动作的历史观察引用
 * @param suggestedStopReason END_ATTEMPT 时建议的停止原因
 */
public record AttackAction(
        AttackActionType actionType,
        String input,
        String parentProbeRef,
        String strategyVersion,
        String reasonSummary,
        List<String> observationRefs,
        AttackStopReason suggestedStopReason) {

    /** D02 单次攻击输入允许保存和提交的最大字符数。 */
    public static final int MAX_INPUT_LENGTH = 100_000;

    /** 创建动作时执行结构化校验并冻结引用列表。 */
    public AttackAction {
        actionType = ContractChecks.required(actionType, "actionType");
        input = ContractChecks.maxLength(
                ContractChecks.nullableRawText(input), MAX_INPUT_LENGTH, "input");
        parentProbeRef = ContractChecks.maxLength(
                ContractChecks.nullableText(parentProbeRef), 96, "parentProbeRef");
        strategyVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(strategyVersion, "strategyVersion"), 128, "strategyVersion");
        reasonSummary = ContractChecks.maxLength(
                ContractChecks.notBlank(reasonSummary, "reasonSummary"), 2_000, "reasonSummary");
        observationRefs = observationRefs == null
                ? List.of()
                : observationRefs.stream()
                .map(value -> ContractChecks.maxLength(
                        ContractChecks.notBlank(value, "observationRefs项"), 96, "observationRefs项"))
                .toList();

        if (actionType == AttackActionType.SEND_INPUT) {
            ContractChecks.notBlankPreserving(input, "SEND_INPUT.input");
            ContractChecks.require(suggestedStopReason == null,
                    "SEND_INPUT不能携带suggestedStopReason");
        }
        if (actionType == AttackActionType.END_ATTEMPT) {
            ContractChecks.require(input == null, "END_ATTEMPT不能携带input");
            ContractChecks.require(suggestedStopReason != null,
                    "END_ATTEMPT必须提供suggestedStopReason");
        }
    }
}
