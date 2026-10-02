package com.kant.agent.contract.budget;

import com.kant.agent.contract.common.ContractChecks;

/**
 * D02 单运行预算账本。
 *
 * <p>D02 先在单个编排器实例内实现原子预留。每次预留发生在动作执行前，
 * 目标调用即使最终为 UNKNOWN 也占用预算。后续异步多实例运行时，持久化
 * 账本可以替换本类，但编排器继续使用相同的预留语义。</p>
 */
public final class BudgetLedger {

    private final BudgetPolicy policy;
    private long targetCalls;
    private long agentCalls;
    private long attempts;
    /** 已结算的目标和 Agent Token 数；当前 D02 的规则 Agent 不产生 Token。 */
    private long tokens;
    /** 已为即将发生的目标调用预留、但还没有结算的输入 Token 数。 */
    private long reservedTokens;

    public BudgetLedger(BudgetPolicy policy) {
        this.policy = ContractChecks.required(policy, "policy");
    }

    /**
     * 从已经持久化的运行快照恢复预算账本。
     *
     * <p>D03 恢复时不能把进程内计数重新置零，否则重启后可能越过原任务预算。
     * 尚未结算的预留 Token 不允许在恢复时被静默释放。</p>
     */
    public BudgetLedger(BudgetPolicy policy, BudgetSnapshot snapshot) {
        this.policy = ContractChecks.required(policy, "policy");
        BudgetSnapshot saved = ContractChecks.required(snapshot, "snapshot");
        this.targetCalls = saved.targetCalls();
        this.agentCalls = saved.agentCalls();
        this.attempts = saved.attempts();
        this.tokens = saved.tokens();
        this.reservedTokens = saved.reservedTokens();
    }

    /** 预留一次目标调用；预留失败表示硬预算已耗尽。 */
    public synchronized boolean reserveTargetCall() {
        if (targetCalls >= policy.maxTargetCalls()) {
            return false;
        }
        targetCalls++;
        return true;
    }

    /**
     * 用已知的物理调用次数结算当前动作预留的目标调用额度。
     *
     * <p>D02 当前一个动作只会先预留一次目标调用，{@code targetCalls} 在结算前
     * 已经包含这次预留。结算时用实际物理调用次数替换这次预留，因此适配器若
     * 在一次逻辑探测内部产生额外调用，也不会让后续动作错误地越过硬上限。</p>
     *
     * @param reservedTargetCalls 本次动作预留的目标调用数
     * @param actualTargetCalls   D01 返回的实际物理目标调用数
     * @return 结算后仍未超过目标调用上限时返回 {@code true}
     */
    public synchronized boolean settleKnownTargetCalls(long reservedTargetCalls,
                                                       long actualTargetCalls) {
        ContractChecks.positive(reservedTargetCalls, "reservedTargetCalls");
        ContractChecks.nonNegative(actualTargetCalls, "actualTargetCalls");
        if (reservedTargetCalls > targetCalls) {
            throw new IllegalArgumentException("结算的目标调用预留超过当前账本计数");
        }

        // D02 是串行推进的，当前预留属于账本末端；未来并发运行时应将
        // 在途目标预留单独建模，而不能继续依赖这个替换语义。
        long settledCalls = targetCalls - reservedTargetCalls;
        targetCalls = safeAdd(settledCalls, actualTargetCalls);
        return targetCalls <= policy.maxTargetCalls();
    }

    /**
     * 在目标结果未知或仍未终态时保守结算目标调用次数。
     *
     * <p>未知结果不能释放已经预留的额度，否则恢复或人工核对期间可能再次
     * 安排超过上限的请求。若执行层已经确认产生了多于预留数的物理调用，
     * 则把超出部分追加到账本。</p>
     *
     * @param reservedTargetCalls 本次动作已经预留的目标调用数
     * @param observedTargetCalls 当前已观察到的物理调用数；不确定时可为0
     * @return 保守记账后仍未超过目标调用上限时返回 {@code true}
     */
    public synchronized boolean settleUnknownTargetCalls(long reservedTargetCalls,
                                                         long observedTargetCalls) {
        ContractChecks.positive(reservedTargetCalls, "reservedTargetCalls");
        ContractChecks.nonNegative(observedTargetCalls, "observedTargetCalls");
        if (reservedTargetCalls > targetCalls) {
            throw new IllegalArgumentException("结算的目标调用预留超过当前账本计数");
        }
        long additionalCalls = Math.max(0L, observedTargetCalls - reservedTargetCalls);
        targetCalls = safeAdd(targetCalls, additionalCalls);
        return targetCalls <= policy.maxTargetCalls();
    }

    /** 预留一次 Agent 调用；预留失败表示硬预算已耗尽。 */
    public synchronized boolean reserveAgentCall() {
        return reserveAgentCalls(1);
    }

    /**
     * 在一次模型调用之前原子预留指定数量的 Agent 调用。
     *
     * <p>批量预留避免循环预留出现只成功一部分的中间状态，也避免大数相加溢出。</p>
     *
     * @param count 本次要预留的调用上限
     * @return 预算足够并完成预留时返回 {@code true}
     */
    public synchronized boolean reserveAgentCalls(long count) {
        ContractChecks.nonNegative(count, "agentCallReservation");
        long remaining = policy.maxAgentCalls() - agentCalls;
        if (count > remaining) {
            return false;
        }
        agentCalls += count;
        return true;
    }

    /** 预留一次攻击尝试。D02 当前只允许一个尝试，但账本保留通用语义。 */
    public synchronized boolean reserveAttempt() {
        if (attempts >= policy.maxAttempts()) {
            return false;
        }
        attempts++;
        return true;
    }

    /**
     * 在目标调用前预留本次输入的估算 Token。
     *
     * <p>预留额度不会立即计入已用 Token，但会参与剩余额度判断，避免两个
     * 后续动作在同一账本中同时越过上限。</p>
     */
    public synchronized boolean reserveInputTokens(long count) {
        ContractChecks.nonNegative(count, "inputTokenReservation");
        long remaining = policy.maxTokens() - tokens - reservedTokens;
        if (count > remaining) {
            return false;
        }
        reservedTokens = safeAdd(reservedTokens, count);
        return true;
    }

    /** 目标调用确定未发出时释放此前的输入 Token 预留。 */
    public synchronized void releaseReservedInputTokens(long count) {
        ContractChecks.nonNegative(count, "inputTokenReservation");
        if (count > reservedTokens) {
            throw new IllegalArgumentException("释放的Token预留超过当前在途预留");
        }
        reservedTokens -= count;
    }

    /**
     * 使用已知的输入和输出 Token 完成本次目标调用结算。
     *
     * @return 结算后仍未超过 Token 上限时返回 {@code true}
     */
    public synchronized boolean settleKnownTargetTokens(long reservedInputTokens,
                                                        long inputTokens,
                                                        long outputTokens) {
        ContractChecks.nonNegative(reservedInputTokens, "reservedInputTokens");
        ContractChecks.nonNegative(inputTokens, "inputTokens");
        ContractChecks.nonNegative(outputTokens, "outputTokens");
        // 先计算完整的新账本值，再释放在途预留。若异常计量发生整数溢出，
        // 保留原预留状态，避免账本出现“额度已释放但消耗未写入”的半结算状态。
        long measuredTokens = safeAdd(inputTokens, outputTokens);
        long settledTokens = safeAdd(tokens, measuredTokens);
        releaseReservedInputTokens(reservedInputTokens);
        tokens = settledTokens;
        return tokens <= policy.maxTokens();
    }

    /**
     * 结算只有部分已知的目标 Token 用量。
     *
     * <p>目标执行已经发生但供应商只返回输入或输出侧部分计量时，已知部分
     * 仍然应该进入账本，未知部分由调用方通过 BLOCKED 状态阻止后续动作。
     * 如果输入侧计量缺失，则使用此前预留的输入估算值保守记账，避免账本
     * 因部分计量而低估实际消耗。</p>
     *
     * @param reservedInputTokens 本次动作预留的输入 Token
     * @param inputTokens         已知的输入 Token，可为空
     * @param outputTokens        已知的输出 Token，可为空
     * @return 结算后仍未超过 Token 上限时返回 {@code true}
     */
    public synchronized boolean settlePartiallyKnownTargetTokens(long reservedInputTokens,
                                                                 Long inputTokens,
                                                                 Long outputTokens) {
        ContractChecks.nonNegative(reservedInputTokens, "reservedInputTokens");
        if (inputTokens == null && outputTokens == null) {
            throw new IllegalArgumentException("部分Token结算至少需要一项已知用量");
        }
        if (inputTokens != null) {
            ContractChecks.nonNegative(inputTokens, "inputTokens");
        }
        if (outputTokens != null) {
            ContractChecks.nonNegative(outputTokens, "outputTokens");
        }

        // 输入计量缺失时不能把已经预留的输入额度释放成零消耗；用预留值
        // 作为保守下限，再叠加已知的输出计量。
        long accountedInputTokens = inputTokens == null ? reservedInputTokens : inputTokens;
        long accountedOutputTokens = outputTokens == null ? 0L : outputTokens;
        long measuredTokens = safeAdd(accountedInputTokens, accountedOutputTokens);
        long settledTokens = safeAdd(tokens, measuredTokens);
        releaseReservedInputTokens(reservedInputTokens);
        tokens = settledTokens;
        return tokens <= policy.maxTokens();
    }

    /**
     * 目标调用已经发生但输出计量不可得时的保守结算。
     *
     * <p>只把已预留的输入记入账本，调用方必须随后将运行置为BLOCKED，
     * 不能把不完整的预算事实当成正常完成。</p>
     */
    public synchronized void settleUnknownTargetTokens(long reservedInputTokens) {
        ContractChecks.nonNegative(reservedInputTokens, "reservedInputTokens");
        releaseReservedInputTokens(reservedInputTokens);
        tokens = safeAdd(tokens, reservedInputTokens);
    }

    /** 返回当前不可变消耗快照。 */
    public synchronized BudgetSnapshot snapshot() {
        return new BudgetSnapshot(targetCalls, agentCalls, attempts, tokens, reservedTokens);
    }

    /** 返回任务配置的预算上限。 */
    public BudgetPolicy policy() {
        return policy;
    }

    /** 防止异常计量导致账本计数溢出后回绕为负数。 */
    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("预算计数溢出", ex);
        }
    }
}
