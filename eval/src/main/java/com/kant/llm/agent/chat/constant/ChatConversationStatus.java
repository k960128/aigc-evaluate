package com.kant.llm.agent.chat.constant;

/**
 * 会话状态枚举
 */
public enum ChatConversationStatus {
    /**
     * ACTIVE 活跃状态，正常使用中
     * ARCHIVED 已归档，不再活跃但保留
     * DELETED 已删除，逻辑删除标记
     */
    ACTIVE, ARCHIVED, DELETED;
}
