package com.kant.springai.agent.agent.multi;

import com.kant.springai.agent.common.construct.AgentContext;

/**
 * 所有 Agent 的统一契约。
 * 每个 Agent 只做一件事，通过共享的 AgentContext（黑板）交换信息。
 */
public interface Agent {

    /** Agent 名称，用于日志与追踪 */
    String name();

    /** Agent 职责描述 */
    String description();

    /** 执行一次任务，结果写入 context */
    void execute(AgentContext context);
}