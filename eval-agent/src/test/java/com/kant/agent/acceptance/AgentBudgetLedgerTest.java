package com.kant.agent.acceptance;

import com.kant.agent.contract.budget.BudgetLedger;
import com.kant.agent.contract.budget.BudgetPolicy;
import com.kant.agent.contract.budget.BudgetSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** D02/D03共享账本的隔离测试；不代替数据库原子更新和Worker恢复验收。 */
class AgentBudgetLedgerTest {

    @Test
    @DisplayName("达到目标调用额度后不再预留下一次调用")
    void boundsTargetCalls() {
        var ledger = new BudgetLedger(policy());
        assertTrue(ledger.reserveTargetCall());
        assertTrue(ledger.reserveTargetCall());
        assertFalse(ledger.reserveTargetCall());
        assertEquals(2L, ledger.snapshot().targetCalls());
    }

    @Test
    @DisplayName("确认调用未发送才退还目标额度")
    void releasesKnownUnsentCall() {
        var ledger = new BudgetLedger(policy());
        assertTrue(ledger.reserveTargetCall());
        assertTrue(ledger.settleKnownTargetCalls(1, 0));
        assertEquals(0L, ledger.snapshot().targetCalls());
    }

    @Test
    @DisplayName("未知调用即使观察计数为零也不能释放预留")
    void retainsUnknownCallReservation() {
        var ledger = new BudgetLedger(policy());
        assertTrue(ledger.reserveTargetCall());
        assertTrue(ledger.settleUnknownTargetCalls(1, 0));
        assertEquals(1L, ledger.snapshot().targetCalls());
    }

    @Test
    @DisplayName("Token预留失败不能产生半成功账目")
    void reservesTokensAtomically() {
        var ledger = new BudgetLedger(policy());
        assertTrue(ledger.reserveInputTokens(80));
        assertFalse(ledger.reserveInputTokens(21));
        assertEquals(new BudgetSnapshot(0, 0, 0, 0, 80), ledger.snapshot());
    }

    @Test
    @DisplayName("已确认输出超限时保留真实账目并阻止后续预留")
    void recordsOverBudgetActualUsage() {
        var ledger = new BudgetLedger(policy());
        assertTrue(ledger.reserveInputTokens(80));
        assertFalse(ledger.settleKnownTargetTokens(80, 80, 30));
        assertEquals(110L, ledger.snapshot().tokens());
        assertEquals(0L, ledger.snapshot().reservedTokens());
        assertFalse(ledger.reserveInputTokens(1));
    }

    @Test
    @DisplayName("恢复保留原调用数和未结算Token，不能从零预算开始")
    void restoresPendingBudget() {
        var saved = new BudgetSnapshot(2, 0, 1, 60, 30);
        var recovered = new BudgetLedger(policy(), saved);
        assertEquals(saved, recovered.snapshot());
        assertFalse(recovered.reserveTargetCall());
        assertFalse(recovered.reserveAttempt());
        assertFalse(recovered.reserveInputTokens(11));
    }

    @Test
    @DisplayName("计量溢出时不能先释放Token预留")
    void preservesReservationOnOverflow() {
        var ledger = new BudgetLedger(policy());
        assertTrue(ledger.reserveInputTokens(10));
        assertThrows(IllegalStateException.class,
                () -> ledger.settleKnownTargetTokens(10, Long.MAX_VALUE, 1));
        assertEquals(new BudgetSnapshot(0, 0, 0, 0, 10), ledger.snapshot());
    }

    private BudgetPolicy policy() {
        return new BudgetPolicy(2, 0, 1, 3, 100, null, null, 120, 1, 0);
    }
}
