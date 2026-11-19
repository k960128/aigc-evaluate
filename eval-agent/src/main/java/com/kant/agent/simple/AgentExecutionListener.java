package com.kant.agent.simple;

/**
 * 反思循环执行回调，由业务模块负责持久化批次和 Agent 日志。
 */
public interface AgentExecutionListener {

    AgentExecutionListener NOOP = new AgentExecutionListener() {
    };

    default void onIterationStarted(int iteration) {
    }

    default void onStarted(String messageId, String type, String promptContent) {
    }

    /**
     * contentResponse 保留模型原文；模型调用失败且无响应时记录异常信息。
     */
    default void onCompleted(String messageId, String contentResponse, Integer tokenCount,
                             int latency, boolean success) {
    }
}
