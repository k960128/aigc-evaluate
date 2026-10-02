package com.kant.agent.acceptance;

import com.kant.agent.contract.probe.ProbeExecutionStatus;
import com.kant.agent.contract.redteam.*;
import com.kant.agent.service.redteam.AttackActionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D02动作围栏：Agent建议不等于授权，非法动作不得进入目标执行。 */
class AgentAttackActionValidatorTest {
    private final AttackActionValidator validator = new AttackActionValidator();

    @Test
    @DisplayName("同一尝试可以引用所属历史，但不得换策略")
    void checksFrozenStrategy() {
        assertDoesNotThrow(() -> validator.validate(send("新输入", "strategy-v1", "execution-1"), context()));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(send("新输入", "strategy-other", "execution-1"), context()));
    }

    @Test
    @DisplayName("拒绝引用其他尝试的观察证据")
    void rejectsForeignObservation() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(send("新输入", "strategy-v1", "foreign-evidence"), context()));
    }

    @Test
    @DisplayName("拒绝引用其他尝试的父探测")
    void rejectsForeignParent() {
        var action = new AttackAction(AttackActionType.SEND_INPUT, "新输入", "foreign-probe",
                "strategy-v1", "虚构机制依据", List.of(), null);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(action, context()));
    }

    @Test
    @DisplayName("同一尝试不得重复发送完全相同输入")
    void rejectsRepeatedInput() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(send("原输入", "strategy-v1", "execution-1"), context()));
    }

    @Test
    @DisplayName("Agent不能用结束动作自行宣称命中风险或未知结果")
    void preventsForgedStopReason() {
        for (AttackStopReason reason : List.of(AttackStopReason.RISK_OBSERVED,
                AttackStopReason.TARGET_RESULT_UNKNOWN, AttackStopReason.USER_STOPPED,
                AttackStopReason.BUDGET_EXHAUSTED)) {
            assertThrows(IllegalArgumentException.class,
                    () -> validator.validate(end(reason), context()), reason.name());
        }
        assertDoesNotThrow(() -> validator.validate(end(AttackStopReason.STRATEGY_EXHAUSTED), context()));
    }

    private AttackAction send(String input, String strategy, String observation) {
        return new AttackAction(AttackActionType.SEND_INPUT, input, null, strategy,
                "仅验证结构化动作围栏", List.of(observation), null);
    }

    private AttackAction end(AttackStopReason reason) {
        return new AttackAction(AttackActionType.END_ATTEMPT, null, null, "strategy-v1",
                "有界策略结束", List.of("execution-1"), reason);
    }

    private AdaptiveAttackContext context() {
        var history = new AttackHistoryItem(1, "probe-1", "execution-1", "原输入",
                ProbeExecutionStatus.SUCCEEDED, "虚构响应", "response-evidence-1", null, null);
        return new AdaptiveAttackContext("task-1", "run-1", "attempt-1", "snapshot-1",
                "fictional-risk", "strategy-v1", "专家种子", List.of(history), 2, 0, 0, false);
    }
}
