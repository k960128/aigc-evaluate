package com.kant.llm.rag.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 安全评测 RAG 查询路由结果。
 *
 * <p>用于表示安全评测查询在检索阶段应该优先使用哪类数据源。
 * 该结果只服务于 RAG 检索路由，不承担业务意图识别职责。</p>
 *
 * @param intent     对当前检索问题的简要概括
 * @param strategy   检索策略：knowledge_base、relational_db
 * @param confidence 路由置信度，取值范围 0 到 1
 * @param reasoning  路由判断理由
 */
public record SecurityEvalQueryRouteResult(
        @JsonPropertyDescription("当前检索问题的简要概括，不要重新输出安全评测业务意图")
        String intent,

        @JsonPropertyDescription("推荐检索策略，只能是 knowledge_base、relational_db 之一")
        String strategy,

        @JsonPropertyDescription("策略推荐置信度，取值范围 0 到 1")
        double confidence,

        @JsonPropertyDescription("选择该检索策略的简要理由")
        String reasoning) {
}
