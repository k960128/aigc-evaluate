package com.kant.agent.contract.redteam;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.probe.ProbeExecutionStatus;

/**
 * 提供给攻击 Agent 的一条历史观察。
 *
 * <p>这里可以包含目标原始响应，因为它只在受控的当前攻击上下文中传递，
 * 不代表可以把原始响应写入普通日志或公开接口。</p>
 *
 * @param sequence            当前尝试内的序号
 * @param probeId             逻辑探测标识
 * @param executionId         物理执行标识
 * @param input               上一轮实际输入
 * @param executionStatus     目标执行状态
 * @param response            目标响应；没有可确认响应时为空
 * @param responseEvidenceRef 目标响应证据引用
 * @param errorCode           执行错误编码
 * @param errorMessage        可安全展示的错误摘要
 */
public record AttackHistoryItem(
        int sequence,
        String probeId,
        String executionId,
        String input,
        ProbeExecutionStatus executionStatus,
        String response,
        String responseEvidenceRef,
        String errorCode,
        String errorMessage) {

    /** 允许传入攻击 Agent 历史上下文的最大响应字符数。 */
    public static final int MAX_RESPONSE_LENGTH = 200_000;

    /** 创建历史观察时校验最小标识，避免 Agent 读取到不完整引用。 */
    public AttackHistoryItem {
        ContractChecks.positive(sequence, "sequence");
        probeId = ContractChecks.maxLength(
                ContractChecks.notBlank(probeId, "probeId"), 64, "probeId");
        executionId = ContractChecks.maxLength(
                ContractChecks.notBlank(executionId, "executionId"), 96, "executionId");
        input = ContractChecks.maxLength(
                ContractChecks.notBlankPreserving(input, "input"),
                AttackAction.MAX_INPUT_LENGTH,
                "input");
        executionStatus = ContractChecks.required(executionStatus, "executionStatus");
        response = ContractChecks.maxLength(
                ContractChecks.nullableRawText(response), MAX_RESPONSE_LENGTH, "response");
        responseEvidenceRef = ContractChecks.maxLength(
                ContractChecks.nullableText(responseEvidenceRef), 96, "responseEvidenceRef");
        errorCode = ContractChecks.maxLength(ContractChecks.nullableText(errorCode), 64, "errorCode");
        errorMessage = ContractChecks.maxLength(ContractChecks.nullableText(errorMessage), 1000, "errorMessage");
    }
}
