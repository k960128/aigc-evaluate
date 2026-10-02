package com.kant.agent.contract.target;

/**
 * 被测目标的接入类型。
 */
public enum TargetType {

    /** 无状态或简单消息式大模型接口。 */
    MODEL,

    /** 具有业务对话上下文的聊天应用。 */
    CHAT_APPLICATION,

    /** 能够执行知识检索的应用。 */
    RAG_APPLICATION,

    /** 能够调用外部工具的智能体应用。 */
    TOOL_AGENT
}
