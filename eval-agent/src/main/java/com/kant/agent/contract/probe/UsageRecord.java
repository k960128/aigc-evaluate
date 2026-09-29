package com.kant.agent.contract.probe;

import com.kant.agent.contract.common.ContractChecks;

import java.math.BigDecimal;

/**
 * 一次探测执行的真实消耗记录。
 *
 * @param targetCalls    实际进入目标客户端调用的次数
 * @param agentCalls     本次探测执行层产生的 Agent 模型调用次数
 * @param inputTokens    输入 Token 数；无法取得时为空
 * @param outputTokens   输出 Token 数；无法取得时为空
 * @param cost           实际或估算费用；无法取得时为空
 * @param currency       费用币种；存在费用时必填
 * @param measurement    计量来源
 */
public record UsageRecord(
        long targetCalls,
        long agentCalls,
        Long inputTokens,
        Long outputTokens,
        BigDecimal cost,
        String currency,
        UsageMeasurement measurement) {

    /**
     * 校验消耗数值与计量状态。
     */
    public UsageRecord {
        ContractChecks.nonNegative(targetCalls, "targetCalls");
        ContractChecks.nonNegative(agentCalls, "agentCalls");
        if (inputTokens != null) {
            ContractChecks.nonNegative(inputTokens, "inputTokens");
        }
        if (outputTokens != null) {
            ContractChecks.nonNegative(outputTokens, "outputTokens");
        }
        if (cost != null) {
            ContractChecks.require(cost.signum() >= 0, "cost不能小于0");
            currency = ContractChecks.maxLength(
                    ContractChecks.notBlank(currency, "currency"), 16, "currency");
        } else {
            ContractChecks.require(currency == null || currency.isBlank(),
                    "未提供cost时不能单独提供currency");
            currency = null;
        }
        measurement = ContractChecks.required(measurement, "measurement");
        if (measurement == UsageMeasurement.UNAVAILABLE) {
            ContractChecks.require(inputTokens == null && outputTokens == null && cost == null,
                    "计量不可用时不能填充Token或费用");
        }
    }
}
