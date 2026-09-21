package com.kant.agent.contract.common;

import java.time.Instant;

import static com.kant.agent.contract.common.ContractChecks.*;

/**
 * 一次智能体执行的实际调用元数据。
 *
 * @param agentRunId   智能体执行 ID，对应 AgentContext.agentRunId
 * @param modelName    实际调用的模型名称
 * @param modelVersion 实际模型版本；供应商未提供时允许为空
 * @param promptVersion 本次使用的提示词版本
 * @param startedAt   开始时间
 * @param finishedAt  结束时间
 * @param inputTokens 输入 Token 数；无法获取时为空，不能用 0 代替未知
 * @param outputTokens 输出 Token 数；无法获取时为空
 */
public record AgentRunMetadata(
        String agentRunId,
        String modelName,
        String modelVersion,
        String promptVersion,
        Instant startedAt,
        Instant finishedAt,
        Long inputTokens,
        Long outputTokens
) {

    public AgentRunMetadata {
        agentRunId = text(agentRunId, "agentRunId");
        modelName = text(modelName, "modelName");
        promptVersion = text(promptVersion, "promptVersion");

        modelVersion = modelVersion == null || modelVersion.isBlank()
                ? null
                : modelVersion.strip();

        required(startedAt, "startedAt");
        required(finishedAt, "finishedAt");

        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "finishedAt 不能早于 startedAt"
            );
        }

        if (inputTokens != null) {
            nonNegative(inputTokens, "inputTokens");
        }
        if (outputTokens != null) {
            nonNegative(outputTokens, "outputTokens");
        }
    }
}