package com.kant.agent.contract.common;

import static com.kant.agent.contract.common.ContractChecks.required;

/**
 * 智能体成功完成一次执行后的结果。
 *
 * @param output   结构化业务输出
 * @param metadata 实际执行元数据
 * @param <T>      业务输出类型
 */
public record AgentResult<T>(
        T output,
        AgentRunMetadata metadata
) {

    public AgentResult {
        required(output, "output");
        required(metadata, "metadata");
    }
}