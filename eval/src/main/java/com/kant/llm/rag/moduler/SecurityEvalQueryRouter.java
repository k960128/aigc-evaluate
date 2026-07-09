package com.kant.llm.rag.moduler;

import com.kant.llm.agent.ai.constant.SecurityEvalIntent;
import com.kant.llm.agent.ai.model.SecurityEvalIntentRecognitionResult;
import com.kant.llm.rag.model.SecurityEvalQueryRouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 安全评测助手专用查询路由器。
 *
 * <p>该组件位于 Query Transformer 之后、具体 Retriever 之前。
 * 它根据已经确认的安全评测意图、槽位和改写后的检索问题，
 * 决定本次查询应走安全评测综合检索入口，还是结构化资产查询入口。</p>
 *
 * <p>注意：knowledge_base 在当前业务中代表安全评测综合检索入口，
 * 其内部可以继续覆盖向量召回、关键词检索、规则知识、判例知识等混合检索能力。
 * 因此 Router 不再单独引入 hybrid 策略。</p>
 */
@Slf4j
public class SecurityEvalQueryRouter implements DocumentRetriever {

    private static final String KNOWLEDGE_BASE = "knowledge_base";
    private static final String RELATIONAL_DB = "relational_db";

    private static final PromptTemplate SECURITY_EVAL_QUERY_ROUTE_PROMPT = PromptTemplate.builder().template("""
            你是大模型安全评测平台的 RAG 查询路由器。

            你的任务不是回答用户问题，也不是重新识别业务意图，而是判断当前检索问题应该使用哪类数据源。

            # 已确认安全评测意图
            {intent}

            # 已抽取槽位
            {entities}

            # 改写后的检索问题
            {query}

            # 可选策略
            1. knowledge_base
               安全评测综合检索入口。适用于评测链路解释、规则依据、风险定义、31 小类知识、判定边界、安全例外、历史判例、
               Bad Case、整改建议、报告辅助、特征解释、误杀漏判原因分析等需要语义理解和多路召回的场景。
               注意：knowledge_base 已经可以覆盖混合检索能力，不需要再选择 hybrid。

            2. relational_db
               结构化资产查询入口。适用于评测任务、评测批次、评测结果明细、规则启停状态、词库状态、特征库状态、风险资产、
               同步状态、误杀统计、漏判统计、时间范围聚合、TopN 排名、数量统计等结构化数据查询。

            # 意图倾向
            - FULL_LINK_TRACE：优先 knowledge_base
            - RULE_FEATURE_TRACE：如果问题关注规则/特征含义、来源和判定依据，选 knowledge_base；如果关注启停状态、版本、同步状态、数量统计，选 relational_db
            - MISJUDGEMENT_ANALYSIS：优先 knowledge_base
            - COMPLIANCE_QA：优先 knowledge_base
            - SIMILAR_CASE_RETRIEVAL：优先 knowledge_base
            - ASSET_DATA_QUERY：优先 relational_db
            - FEATURE_EXTRACTION：优先 knowledge_base
            - REPORT_ASSISTANCE：优先 knowledge_base
            - GENERAL_CHAT：优先 knowledge_base

            请严格输出 JSON，不要输出 Markdown、解释性前缀或额外文本。

            {{
              "intent": "对当前检索问题的简要概括",
              "strategy": "knowledge_base",
              "confidence": 0.90,
              "reasoning": "选择该策略的简要理由"
            }}
            """).build();

    private final List<DocumentRetriever> documentRetrievers;
    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final SecurityEvalIntent intent;
    private final SecurityEvalIntentRecognitionResult.Entities entities;
    private final Consumer<String> progressCallback;
    private final AtomicBoolean routeProgressSent = new AtomicBoolean(false);

    public SecurityEvalQueryRouter(List<DocumentRetriever> documentRetrievers,
                                   ChatClient chatClient,
                                   SecurityEvalIntent intent,
                                   SecurityEvalIntentRecognitionResult.Entities entities,
                                   Consumer<String> progressCallback) {
        this(documentRetrievers,
                SECURITY_EVAL_QUERY_ROUTE_PROMPT,
                chatClient,
                intent,
                entities,
                progressCallback);
    }

    public SecurityEvalQueryRouter(List<DocumentRetriever> documentRetrievers,
                                   PromptTemplate promptTemplate,
                                   ChatClient chatClient,
                                   SecurityEvalIntent intent,
                                   SecurityEvalIntentRecognitionResult.Entities entities,
                                   Consumer<String> progressCallback) {
        this.documentRetrievers = documentRetrievers == null
                ? List.of()
                : documentRetrievers.stream().filter(Objects::nonNull).toList();
        this.promptTemplate = promptTemplate == null ? SECURITY_EVAL_QUERY_ROUTE_PROMPT : promptTemplate;
        this.chatClient = chatClient;
        this.intent = intent;
        this.entities = entities;
        this.progressCallback = progressCallback;
    }

    @Override
    public List<Document> retrieve(Query query) {
        List<DocumentRetriever> routedRetrievers = route(query);

        if (routedRetrievers == null || routedRetrievers.isEmpty()) {
            log.info("安全评测查询未路由到任何数据源, intent={}, query={}", intent, query.text());
            return List.of();
        }

        return routedRetrievers.stream()
                .flatMap(retriever -> retriever.retrieve(query).stream())
                .toList();
    }

    /**
     * 根据安全评测意图、槽位和检索问题选择 Retriever。
     *
     * <p>优先使用 LLM 路由结果；当模型输出为空、策略非法或置信度过低时，
     * 使用基于安全评测 intent 的确定性兜底策略。</p>
     *
     * @param query 改写后的检索问题
     * @return 本次应调用的 Retriever 列表
     */
    public List<DocumentRetriever> route(Query query) {
        if (progressCallback != null && routeProgressSent.compareAndSet(false, true)) {
            progressCallback.accept("[PROGRESS]:正在路由安全评测检索数据源...");
        }

        String strategy = fallbackStrategy();

        try {
            SecurityEvalQueryRouteResult routeResult = chatClient.prompt(createPrompt(query))
                    .call()
                    .entity(SecurityEvalQueryRouteResult.class);

            if (routeResult != null && isSupportedStrategy(routeResult.strategy()) && routeResult.confidence() >= 0.45) {
                strategy = routeResult.strategy();
                log.info("安全评测查询路由成功, intent={}, strategy={}, confidence={}, query={}, reasoning={}",
                        intent, strategy, routeResult.confidence(), query.text(), routeResult.reasoning());
            } else {
                log.warn("安全评测查询路由结果不可用，使用兜底策略, intent={}, fallbackStrategy={}, query={}, routeResult={}",
                        intent, strategy, query.text(), routeResult);
            }
        } catch (Exception e) {
            log.warn("安全评测查询路由异常，使用兜底策略, intent={}, fallbackStrategy={}, query={}",
                    intent, strategy, query.text(), e);
        }

        return selectRetrievers(strategy);
    }

    private Prompt createPrompt(Query query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("intent", intent == null ? "" : intent.name());
        variables.put("entities", formatEntities(entities));
        variables.put("query", query.text());
        return promptTemplate.create(variables);
    }

    private List<DocumentRetriever> selectRetrievers(String strategy) {
        return switch (strategy) {
            case RELATIONAL_DB -> byType(EvaSqlDatabaseContentRetriever.class);
            case KNOWLEDGE_BASE -> byType(VectorStoreDocumentRetriever.class);
            default -> byType(VectorStoreDocumentRetriever.class);
        };
    }

    private List<DocumentRetriever> byType(Class<?> retrieverType) {
        List<DocumentRetriever> retrievers = documentRetrievers.stream()
                .filter(retrieverType::isInstance)
                .collect(Collectors.toList());
        return retrievers.isEmpty() ? documentRetrievers : retrievers;
    }

    private boolean isSupportedStrategy(String strategy) {
        return KNOWLEDGE_BASE.equals(strategy)
                || RELATIONAL_DB.equals(strategy);
    }

    private String fallbackStrategy() {
        if (intent == null) {
            return KNOWLEDGE_BASE;
        }
        return switch (intent) {
            case ASSET_DATA_QUERY -> RELATIONAL_DB;
            case RULE_FEATURE_TRACE -> hasStructuredAssetSlot() ? RELATIONAL_DB : KNOWLEDGE_BASE;
            default -> KNOWLEDGE_BASE;
        };
    }

    private boolean hasStructuredAssetSlot() {
        if (entities == null) {
            return false;
        }
        return entities.rule_id() != null
                || entities.feature_id() != null
                || entities.keyword_id() != null
                || entities.task_id() != null
                || entities.task_detail_id() != null
                || entities.result_detail_id() != null;
    }

    private String formatEntities(SecurityEvalIntentRecognitionResult.Entities e) {
        if (e == null) {
            return "无";
        }

        List<String> parts = new ArrayList<>();
        add(parts, "input_text", e.input_text());
        add(parts, "model_output", e.model_output());
        add(parts, "risk_details_id", e.risk_details_id());
        add(parts, "risk_details_name", e.risk_details_name());
        add(parts, "category_id", e.category_id());
        add(parts, "category_name", e.category_name());
        add(parts, "scenario", e.scenario());
        add(parts, "model_name", e.model_name());
        add(parts, "task_id", e.task_id());
        add(parts, "task_detail_id", e.task_detail_id());
        add(parts, "result_detail_id", e.result_detail_id());
        add(parts, "dataset_id", e.dataset_id());
        add(parts, "sample_id", e.sample_id());
        add(parts, "rule_id", e.rule_id());
        add(parts, "feature_id", e.feature_id());
        add(parts, "keyword_id", e.keyword_id());
        add(parts, "time_range", e.time_range());
        add(parts, "top_k", e.top_k());
        add(parts, "l2_threshold", e.l2_threshold());
        add(parts, "l2_top_k", e.l2_top_k());
        add(parts, "risk_scope_ids", e.risk_scope_ids());
        add(parts, "judge_version", e.judge_version());
        add(parts, "output_format", e.output_format());
        add(parts, "candidate_features", e.candidate_features());
        add(parts, "keywords", e.keywords());

        return parts.isEmpty() ? "无" : String.join("\n", parts);
    }

    private void add(List<String> parts, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        parts.add(name + "=" + value);
    }
}
