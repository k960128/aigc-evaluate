package com.kant.llm.rag.moduler;

import com.google.common.base.Stopwatch;
import com.kant.llm.agent.ai.constant.SecurityEvalIntent;
import com.kant.llm.agent.ai.model.SecurityEvalIntentRecognitionResult;
import com.kant.llm.agent.chat.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.util.stream.Collectors.joining;

/**
 * 安全评测助手专用问题改写器。
 *
 * <p>该组件位于安全评测意图识别和 Slot Validator 之后，RAG 检索之前。
 * 它不会重新判断用户意图，而是基于已经确定的 intent 和 entities，
 * 将用户原始问题改写成更适合安全评测知识库、评测资产库或诊断数据检索的查询语句。</p>
 */
@Slf4j
public class SecurityEvalQueryTransformer implements QueryTransformer {

    private static final PromptTemplate SECURITY_EVAL_QUERY_REWRITE_PROMPT = PromptTemplate.builder().template("""
            你是大模型安全评测平台的检索问题改写器。

            你的任务不是回答用户问题，也不是重新识别意图，而是根据已经确定的安全评测意图和槽位，
            将用户问题改写成适合 RAG 检索、资产查询或诊断上下文检索的独立检索语句。

            # 已确认意图
            {intent}

            # 已抽取槽位
            {entities}

            # 历史对话摘要
            {chatMemory}

            # 用户原始问题
            {query}

            # 改写要求
            1. 只能输出改写后的检索问题，不要输出解释、分析过程、Markdown 或多余前缀。
            2. 必须保留明确的 ID、风险小类、规则、特征、时间范围、模型名称、任务信息等关键槽位。
            3. 不要生成攻击样本、测试样本或红队 payload。
            4. 如果原问题已经足够清晰，可以在保留原意的基础上补充安全评测检索关键词。
            5. 根据不同 intent 使用不同改写策略：
               - FULL_LINK_TRACE：强调 L1/L2/L3 判定链路、命中规则、向量召回、L3 裁判结果。
               - RULE_FEATURE_TRACE：强调规则、词库、特征、风险小类、版本、状态、来源、同步关系。
               - MISJUDGEMENT_ANALYSIS：强调误杀、漏判、阈值、白样本、向量边界、裁判规则、相似案例。
               - COMPLIANCE_QA：强调风险定义、判定边界、安全例外、规则依据、31 小类知识。
               - SIMILAR_CASE_RETRIEVAL：强调历史 Bad Case、人工判例、相似输入输出、判罚一致性。
               - ASSET_DATA_QUERY：强调统计指标、时间范围、风险资产、规则/词库/特征库、评测结果。
               - FEATURE_EXTRACTION：强调待审核风险特征、判定线索、关键词、来源文本。
               - REPORT_ASSISTANCE：强调评测任务、批次、结果摘要、整改建议、复测建议。
            """).build();

    private final ChatClient chatClient;
    private final ChatMessageService chatMessageService;
    private final SecurityEvalIntent intent;
    private final SecurityEvalIntentRecognitionResult.Entities entities;
    private final String chatMessageId;
    private final Consumer<String> progressCallback;

    public SecurityEvalQueryTransformer(ChatClient chatClient,
                                        ChatMessageService chatMessageService,
                                        SecurityEvalIntent intent,
                                        SecurityEvalIntentRecognitionResult.Entities entities,
                                        String chatMessageId,
                                        Consumer<String> progressCallback) {
        this.chatClient = chatClient;
        this.chatMessageService = chatMessageService;
        this.intent = intent;
        this.entities = entities;
        this.chatMessageId = chatMessageId;
        this.progressCallback = progressCallback;
    }

    @Override
    public Query transform(Query query) {
        if (progressCallback != null) {
            progressCallback.accept("[PROGRESS]:正在优化安全评测检索问题...");
        }

        log.info("开始安全评测问题改写, intent={}, sourceQuery={}", intent, query.text());
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            String rewrittenQuery = chatClient
                    .prompt(createPrompt(query, format(query.history())))
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();

            rewrittenQuery = normalize(rewrittenQuery, query.text());
            Query transformedQuery = query.mutate().text(rewrittenQuery).build();

            log.info("安全评测问题改写完成, intent={}, rewrittenQuery={}, elapsedMs={}",
                    intent, rewrittenQuery, stopwatch.stop().elapsed(TimeUnit.MILLISECONDS));

            writeBackTransformContent(rewrittenQuery);
            return transformedQuery;
        } catch (Exception e) {
            log.warn("安全评测问题改写失败，回退为原始问题: {}", query.text(), e);
            return query;
        }
    }

    private Prompt createPrompt(Query query, String chatMemory) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("intent", intent == null ? "" : intent.name());
        variables.put("entities", formatEntities(entities));
        variables.put("chatMemory", chatMemory == null ? "" : chatMemory);
        variables.put("query", query.text());
        return SECURITY_EVAL_QUERY_REWRITE_PROMPT.create(variables);
    }

    private String normalize(String rewrittenQuery, String fallback) {
        if (rewrittenQuery == null || rewrittenQuery.isBlank()) {
            return fallback;
        }
        return rewrittenQuery
                .replace("```", "")
                .replace("改写后的检索问题：", "")
                .replace("改写结果：", "")
                .trim();
    }

    private void writeBackTransformContent(String rewrittenQuery) {
        if (chatMessageId == null || chatMessageId.isBlank() || chatMessageService == null) {
            return;
        }
        Thread.ofVirtual().name("security-eval-query-transform-" + chatMessageId).start(() -> {
            try {
                chatMessageService.updateTransformContent(chatMessageId, rewrittenQuery);
                log.info("安全评测问题改写结果已回写: messageId={}, transformContent={}", chatMessageId, rewrittenQuery);
            } catch (Exception e) {
                log.warn("安全评测问题改写结果回写失败: messageId={}", chatMessageId, e);
            }
        });
    }

    private String format(List<Message> chatMemory) {
        if (chatMemory == null || chatMemory.isEmpty()) {
            return "";
        }
        return chatMemory.stream()
                .map(this::format)
                .filter(Objects::nonNull)
                .collect(joining("\n"));
    }

    private String format(Message message) {
        if (message instanceof UserMessage userMessage) {
            return "User: " + userMessage.getText();
        } else if (message instanceof AssistantMessage assistantMessage) {
            if (assistantMessage.hasToolCalls()) {
                return null;
            }
            return "AI: " + assistantMessage.getText();
        }
        return null;
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
