package com.kant.agent.service.redteam;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.redteam.AdaptiveAttackContext;
import com.kant.agent.contract.redteam.AttackAction;
import com.kant.agent.contract.redteam.AttackActionType;
import com.kant.agent.contract.redteam.AttackHistoryItem;
import com.kant.agent.contract.redteam.AttackStopReason;

import java.util.EnumSet;
import java.util.Set;

/**
 * 对攻击 Agent 输出的动作执行确定性校验。
 *
 * <p>该校验必须位于 Agent 和目标执行端口之间。Agent 提议的策略版本、父探测
 * 和观察引用都不能直接被信任。</p>
 */
public final class AttackActionValidator {

    /**
     * 只能由编排器根据真实执行事实确认的停止原因。
     *
     * <p>这些原因不能由模型通过 END_ATTEMPT 自行声明，否则模型输出就可能
     * 把风险命中、用户停止、预算耗尽或执行未知伪造成正常策略结束。风险
     * 命中和目标执行结果必须由编排器根据结构化观察确认。</p>
     */
    private static final Set<AttackStopReason> ORCHESTRATOR_ONLY_STOP_REASONS =
            EnumSet.of(
                    AttackStopReason.BUDGET_EXHAUSTED,
                    AttackStopReason.BUDGET_USAGE_UNKNOWN,
                    AttackStopReason.GENERATION_FAILED,
                    AttackStopReason.ACTION_REJECTED,
                    AttackStopReason.RISK_OBSERVED,
                    AttackStopReason.TARGET_RESULT_UNKNOWN,
                    AttackStopReason.TARGET_EXECUTION_FAILED,
                    AttackStopReason.USER_STOPPED,
                    AttackStopReason.TIME_LIMIT_REACHED);

    /**
     * 校验动作是否仍处于当前尝试的授权范围内。
     *
     * @param action  Agent 提议动作
     * @param context 当前攻击上下文
     */
    public void validate(AttackAction action, AdaptiveAttackContext context) {
        ContractChecks.required(action, "action");
        ContractChecks.required(context, "context");
        ContractChecks.require(context.strategyVersion().equals(action.strategyVersion()),
                "动作策略版本与任务固定版本不一致");

        if (action.parentProbeRef() != null) {
            boolean exists = context.history().stream()
                    .anyMatch(item -> action.parentProbeRef().equals(item.probeId())
                            || action.parentProbeRef().equals(item.executionId()));
            ContractChecks.require(exists, "parentProbeRef不属于当前攻击尝试");
        }

        for (String observationRef : action.observationRefs()) {
            boolean exists = context.history().stream()
                    .anyMatch(item -> observationRef.equals(item.probeId())
                            || observationRef.equals(item.executionId())
                            || observationRef.equals(item.responseEvidenceRef()));
            ContractChecks.require(exists, "observationRef不属于当前攻击尝试：" + observationRef);
        }

        // END_ATTEMPT 如果携带观察引用，同样必须来自当前尝试；首轮直接结束
        // 可以没有历史观察，但不能借结束动作伪造其他运行的依据。
        if (action.actionType() == AttackActionType.END_ATTEMPT) {
            ContractChecks.require(
                    !ORCHESTRATOR_ONLY_STOP_REASONS.contains(action.suggestedStopReason()),
                    "该停止原因只能由编排器根据执行事实确认");
            return;
        }

        ContractChecks.notBlankPreserving(action.input(), "SEND_INPUT.input");
        for (AttackHistoryItem historyItem : context.history()) {
            ContractChecks.require(!historyItem.input().equals(action.input()),
                    "禁止在同一尝试中重复发送完全相同的输入");
        }
    }
}
