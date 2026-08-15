package com.kant.llm.agent.chat.constant;

/**
 * 检索来源枚举
 */
public enum RetrievalSource {

    /**
     * VECTOR 向量检索
     * KEYWORD 关键词检索
     * HYBRID 混合检索
     * RERANK 重排序后结果
     */
    VECTOR, KEYWORD, HYBRID, RERANK
}
