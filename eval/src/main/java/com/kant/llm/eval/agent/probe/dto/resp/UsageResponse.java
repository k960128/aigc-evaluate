package com.kant.llm.eval.agent.probe.dto.resp;

import com.kant.agent.contract.probe.UsageRecord;

import java.math.BigDecimal;

/**
 * 一次探测执行的消耗响应。
 *
 * @param targetCalls  实际进入目标客户端调用的次数
 * @param agentCalls   本次探测执行层产生的 Agent 模型调用次数
 * @param inputTokens  输入 Token；无法取得时为 {@code null}
 * @param outputTokens 输出 Token；无法取得时为 {@code null}
 * @param cost         费用；无法取得时为 {@code null}
 * @param currency     费用币种；没有费用时为 {@code null}
 * @param measurement  ACTUAL、ESTIMATED 或 UNAVAILABLE
 */
public record UsageResponse(
        long targetCalls,
        long agentCalls,
        Long inputTokens,
        Long outputTokens,
        BigDecimal cost,
        String currency,
        String measurement) {

    /** 将领域消耗记录转换为 API 响应。 */
    public static UsageResponse from(UsageRecord usage) {
        return new UsageResponse(
                usage.targetCalls(),
                usage.agentCalls(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cost(),
                usage.currency(),
                usage.measurement().name());
    }
}
