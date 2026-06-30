package com.kant.llm.rag.moduler;

import com.kant.llm.rag.model.QueryRouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 问题查询路由
 */
@Slf4j
public class EvalQueryRouter {

    private final List<DocumentRetriever> documentRetrievers;

    protected final ChatClient chatClient;

    protected final PromptTemplate promptTemplate;

    /**
     * 进度回调，用于流式返回前端进度信息
     */
    private final Consumer<String> progressCallback;

    /**
     * 确保路由进度只发送一次（DefaultRetrievalAugmentor 可能对多个 query 多次调用 route）
     */
    private final AtomicBoolean routeProgressSent = new AtomicBoolean(false);

    private static final PromptTemplate QUERY_ROUTE_PROMPT = PromptTemplate.builder().template("""
            你是一个汽车领域的智能助手，负责理解用户的问题，并智能判断最适合的数据查询方式。你的任务不是直接回答问题，而是分析问题语义，决定应调用哪种或哪几种数据源来获取答案。
            
            请根据以下规则进行判断：
            1、关系型数据库（Relational DB）适用场景：
            问题涉及结构化数据查询（如“车辆信息”、“保险信息”、“订单信息”等）
            问题涉及到用户个人拥有的车辆相关信息的查询的，如查询发动机号、查询下次保养时间、查询车辆里程等
            包含明确的实体属性、时间范围、数值比较、聚合操作（如 SUM、COUNT、AVG）
            示例：“我的保险还有多少天到期？”
            
            2、图数据库（Graph DB）适用场景：
            问题关注实体之间的关系、路径、连接性、层级或网络结构
            出现关键词如“谁的发动机是...”、“A和B之间有什么联系？”、“最短路径”、“影响链”
            示例：“纯电车型都有哪些？”、“型号A和型号B有什么关系？”
            
            3、知识库检索适用场景：
            问题基于语义相似性、模糊匹配、非结构化文本理解
            涉及“类似”、“相关”、“推荐”、“总结”、“解释某段内容”等意图
            涉及到汽车相关售前、售后、技术支持、营销政策等问题
            示例：“发动机异响怎么处理？”、“如何打开零重力座椅？”
            
            请严格按以下 JSON 格式输出决策结果，不要添加额外解释，不要添加任何markdown符号，如[```]：
            
            {{
              "intent": "简要概括用户问题的核心意图",
              "strategy": "relational_db"
              "reasoning": "简明说明判断依据",
              "confidence": 置信度，0-1之间的小数
            }}
            
            注意：
            strategy 仅使用以下三个字符串值："relational_db"、"graph_db"、"knowledge_base"，其一次只返回一个。
            confidence 表示你对策略推荐的置信度（0–1），评分保留两位小数
            reasoning 应简洁说明判断依据
            
            用户的原始查询：{query}
            """).build();

    public EvalQueryRouter(List<DocumentRetriever> documentRetrievers, ChatClient chatClient) {
        this(documentRetrievers, QUERY_ROUTE_PROMPT, chatClient, null);
    }

    public EvalQueryRouter(List<DocumentRetriever> documentRetrievers, ChatClient chatClient, Consumer<String> progressCallback) {
        this(documentRetrievers, QUERY_ROUTE_PROMPT, chatClient, progressCallback);
    }

    public EvalQueryRouter(List<DocumentRetriever> documentRetrievers, PromptTemplate promptTemplate, ChatClient chatClient, Consumer<String> progressCallback) {
        this.promptTemplate = promptTemplate;
        this.documentRetrievers = documentRetrievers;
        this.chatClient = chatClient;
        this.progressCallback = progressCallback;
    }

    /**
     * 根据查询路由到合适的文档检索器
     * @param  query  用户查询
     * @return 适配的文档检索器列表，可能为空
     */
    public List<DocumentRetriever> route(Query query) {
        // 发送进度：开始问题路由（仅发送一次，避免多个 query 导致重复）
        if (progressCallback != null && routeProgressSent.compareAndSet(false, true)) {
            progressCallback.accept("[PROGRESS]:正在路由您的问题...");
            System.out.println("[PROGRESS]:正在路由您的问题...");
        }

        QueryRouteResult queryRouteResult = chatClient.prompt(createPrompt(query)).call().entity(QueryRouteResult.class);

        try {
            String strategy = queryRouteResult.strategy();
            log.info("Route Success , query: {} , strategy: {}", query, strategy);

            switch (strategy) {
                case "relational_db":
                    return documentRetrievers.stream().filter(retriever -> retriever instanceof EvaSqlDatabaseContentRetriever).collect(Collectors.toList());
                case "knowledge_base":
                    return documentRetrievers.stream().filter(retriever -> retriever instanceof VectorStoreDocumentRetriever).collect(Collectors.toList());
                default:
                    return documentRetrievers;
            }
        } catch (Exception e) {
            log.error("Route Error , query: {}", query, e);
        }
        return List.of();
    }

    protected Prompt createPrompt(Query query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.text());
        return promptTemplate.create(variables);
    }
}
