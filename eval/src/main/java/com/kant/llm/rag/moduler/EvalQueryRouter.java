package com.kant.llm.rag.moduler;

import com.kant.llm.rag.model.QueryRouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class EvalQueryRouter implements DocumentRetriever {

    private final List<DocumentRetriever> documentRetrievers;

    protected final ChatClient chatClient;

    protected final String conversationId;

    protected final PromptTemplate promptTemplate;

    private final Consumer<String> progressCallback;

    private final AtomicBoolean routeProgressSent = new AtomicBoolean(false);

    private static final PromptTemplate QUERY_ROUTE_PROMPT = PromptTemplate.builder().template("""
            你是一个智能查询路由器，负责理解用户问题，并判断最适合的数据查询方式。
            你的任务不是直接回答问题，而是分析问题语义，决定应该调用哪种数据源来获取答案。
            
            请根据以下规则判断：
            
            1. relational_db
            适用于结构化数据查询。
            例如：订单信息、用户信息、车辆信息、保险信息、状态查询、数量统计、时间范围查询、金额查询等。
            如果问题包含明确实体属性、时间范围、数值比较、聚合操作，例如 COUNT、SUM、AVG，也应选择 relational_db。
            
            2. knowledge_base
            适用于知识库语义检索。
            例如：概念解释、政策说明、操作指南、故障处理、相似问题、总结、推荐、文档内容理解等。
            
            请严格按照以下 JSON 格式输出，不要添加额外解释，不要添加 markdown：
            
            {{
              "intent": "简要概括用户问题的核心意图",
              "strategy": "relational_db",
              "reasoning": "简要说明判断依据",
              "confidence": 0.90
            }}
            
            注意：
            strategy 只能使用以下值之一：
            "relational_db"、"knowledge_base"
            
            confidence 表示你对策略推荐的置信度，取值范围是 0 到 1。
            
            用户原始查询：
            {query}
            """).build();

    public EvalQueryRouter(List<DocumentRetriever> documentRetrievers,
                           ChatClient chatClient,
                           String conversationId) {
        this(documentRetrievers, QUERY_ROUTE_PROMPT, chatClient, conversationId, null);
    }

    public EvalQueryRouter(List<DocumentRetriever> documentRetrievers,
                           ChatClient chatClient,
                           String conversationId,
                           Consumer<String> progressCallback) {
        this(documentRetrievers, QUERY_ROUTE_PROMPT, chatClient, conversationId, progressCallback);
    }

    public EvalQueryRouter(List<DocumentRetriever> documentRetrievers,
                           PromptTemplate promptTemplate,
                           ChatClient chatClient,
                           String conversationId,
                           Consumer<String> progressCallback) {
        this.promptTemplate = promptTemplate == null ? QUERY_ROUTE_PROMPT : promptTemplate;
        this.documentRetrievers = documentRetrievers == null
                ? List.of()
                : documentRetrievers.stream().filter(Objects::nonNull).toList();
        this.chatClient = chatClient;
        this.progressCallback = progressCallback;
        this.conversationId = conversationId;
    }

    @Override
    public List<Document> retrieve(Query query) {
        List<DocumentRetriever> routedRetrievers = route(query);

        if (routedRetrievers == null || routedRetrievers.isEmpty()) {
            log.info("No retriever routed, query: {}", query.text());
            return List.of();
        }

        return routedRetrievers.stream()
                .flatMap(retriever -> retriever.retrieve(query).stream())
                .toList();
    }

    public List<DocumentRetriever> route(Query query) {
        if (progressCallback != null && routeProgressSent.compareAndSet(false, true)) {
            progressCallback.accept("[PROGRESS]:正在路由您的问题...");
        }

        try {
            QueryRouteResult queryRouteResult = chatClient.prompt(createPrompt(query))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(QueryRouteResult.class);

            if (queryRouteResult == null || queryRouteResult.strategy() == null) {
                log.warn("Route result is empty, query: {}", query.text());
                return documentRetrievers;
            }

            String strategy = queryRouteResult.strategy();
            log.info("Route Success, query: {}, strategy: {}, confidence: {}",
                    query.text(), strategy, queryRouteResult.confidence());

            return switch (strategy) {
                case "relational_db" -> documentRetrievers.stream()
                        .filter(retriever -> retriever instanceof EvaSqlDatabaseContentRetriever)
                        .collect(Collectors.toList());

                case "knowledge_base" -> documentRetrievers.stream()
                        .filter(retriever -> retriever instanceof VectorStoreDocumentRetriever)
                        .collect(Collectors.toList());

                default -> documentRetrievers;
            };

        } catch (Exception e) {
            log.error("Route Error, query: {}", query.text(), e);
            return List.of();
        }
    }

    protected Prompt createPrompt(Query query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.text());
        return promptTemplate.create(variables);
    }
}