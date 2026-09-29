package com.kant.agent.contract.probe;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.contract.target.TargetReference;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 向共享探测执行端口提交的一次受控调用命令。
 *
 * @param idempotencyKey      调用方生成的幂等键；同一租户内同键不同参数必须冲突
 * @param context             执行业务上下文
 * @param probeId             逻辑探测标识
 * @param target              被测目标引用
 * @param riskId              本次探测对应的风险编码
 * @param ruleSetVersion      本次后续判定应使用的规则集版本
 * @param executionMode       目标交互模式
 * @param sessionRef          目标会话引用；单轮模式必须为空
 * @param input               实际提交给目标的输入正文
 * @param requiredCapabilities 本次探测明确要求的目标能力
 * @param deadlineAt          最晚开始执行时间；为空表示使用服务端默认超时策略
 */
public record ProbeCommand(
        String idempotencyKey,
        ExecutionContext context,
        String probeId,
        TargetReference target,
        String riskId,
        String ruleSetVersion,
        ExecutionMode executionMode,
        String sessionRef,
        String input,
        Set<TargetCapability> requiredCapabilities,
        Instant deadlineAt) {

    /**
     * 规范化命令内容并冻结能力集合。
     */
    public ProbeCommand {
        idempotencyKey = ContractChecks.maxLength(
                ContractChecks.notBlank(idempotencyKey, "idempotencyKey"), 128, "idempotencyKey");
        context = ContractChecks.required(context, "context");
        probeId = ContractChecks.maxLength(
                ContractChecks.notBlank(probeId, "probeId"), 64, "probeId");
        target = ContractChecks.required(target, "target");
        riskId = ContractChecks.maxLength(
                ContractChecks.notBlank(riskId, "riskId"), 128, "riskId");
        ruleSetVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(ruleSetVersion, "ruleSetVersion"), 128, "ruleSetVersion");
        executionMode = ContractChecks.required(executionMode, "executionMode");
        sessionRef = ContractChecks.maxLength(
                ContractChecks.nullableText(sessionRef), 96, "sessionRef");
        // 只校验输入，不能去除首尾空白；空白本身可能是安全攻击样本的一部分。
        ContractChecks.notBlank(input, "input");
        input = ContractChecks.maxLength(input, 100_000, "input");
        if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
            requiredCapabilities = Collections.emptySet();
        } else {
            requiredCapabilities = Collections.unmodifiableSet(EnumSet.copyOf(requiredCapabilities));
        }
        if (executionMode != ExecutionMode.ADAPTIVE_MULTI_TURN) {
            ContractChecks.require(sessionRef == null, "单轮模式不能携带sessionRef");
        }
    }
}
