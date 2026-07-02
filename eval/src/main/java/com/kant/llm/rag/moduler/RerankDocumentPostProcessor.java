package com.kant.llm.rag.moduler;

import com.kant.ai.infra.rerank.RerankService;
import com.kant.ai.infra.rerank.RerankServiceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * RAG 文档重排序后处理器。
 *
 * <p>职责边界：本类只负责“合并后的候选文档精排”，不负责召回、不负责 RRF 融合、不负责 prompt 注入。</p>
 *
 * <p>它复用 infra-ai 模块中的 {@link RerankService}，将候选 Document.text 交给重排序模型，
 * 再按模型返回的相关性分数排序并截取 topK。</p>
 *
 * <p>如果 Rerank 服务不可用、降级或返回结构无法解析，本类会回退到已有 score / rrfScore 排序，
 * 避免阻断 RAG 主流程。</p>
 */
@Slf4j
public class RerankDocumentPostProcessor implements DocumentPostProcessor {

    /** Rerank 模型返回的相关性分数。 */
    public static final String METADATA_RERANK_SCORE = "rerankScore";

    /** 默认最终注入 prompt 的文档数量。 */
    private static final int DEFAULT_TOP_K = 5;

    private final RerankService rerankService;

    private final int topK;

    private final String modelIdOrCode;

    public RerankDocumentPostProcessor(RerankService rerankService) {
        this(rerankService, DEFAULT_TOP_K, null);
    }

    public RerankDocumentPostProcessor(RerankService rerankService, int topK) {
        this(rerankService, topK, null);
    }

    public RerankDocumentPostProcessor(RerankService rerankService, int topK, String modelIdOrCode) {
        this.rerankService = rerankService;
        this.topK = topK <= 0 ? DEFAULT_TOP_K : topK;
        this.modelIdOrCode = modelIdOrCode;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (query == null || !StringUtils.hasText(query.text()) || rerankService == null) {
            // 缺少 query 或 RerankService 时不抛异常，直接使用已有分数兜底。
            return fallback(documents);
        }

        // Rerank 模型只处理有正文的文本候选；空文本无法进入精排。
        List<Document> candidates = documents.stream()
                .filter(document -> document != null && StringUtils.hasText(document.getText()))
                .toList();
        if (candidates.isEmpty()) {
            return fallback(documents);
        }

        List<String> texts = candidates.stream()
                .map(Document::getText)
                .map(String::trim)
                .toList();
        // 默认使用 infra-ai 的默认 Rerank 模型；传入 modelIdOrCode 时可显式指定模型。
        RerankServiceResult result = StringUtils.hasText(modelIdOrCode)
                ? rerankService.rerank(modelIdOrCode, query.text(), texts)
                : rerankService.rerank(query.text(), texts);
        if (result == null || Boolean.TRUE.equals(result.getDegraded()) || result.getRawResponse() == null) {
            log.warn("RAG rerank degraded, reason: {}", result == null ? "RerankServiceResult is null" : result.getReason());
            return fallback(documents);
        }

        List<RerankedDocument> rerankedDocuments = convertResponse(result.getRawResponse(), candidates);
        if (rerankedDocuments.isEmpty()) {
            log.warn("RAG rerank response cannot be mapped to documents, fallback to existing score.");
            return fallback(documents);
        }

        // 将 Rerank 原始响应映射回候选 Document，并按 rerankScore 降序截断。
        return rerankedDocuments.stream()
                .sorted(Comparator.comparing(RerankedDocument::score).reversed())
                .limit(topK)
                .map(RerankedDocument::toDocument)
                .toList();
    }

    /**
     * Rerank 不可用时的兜底排序。
     *
     * <p>优先使用 Document.score；没有 score 时尝试使用 RRF 阶段写入的 rrfScore。</p>
     */
    private List<Document> fallback(List<Document> documents) {
        return documents.stream()
                .filter(document -> document != null && StringUtils.hasText(document.getText()))
                .sorted(Comparator.comparing(this::fallbackScore).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 解析兜底分数。
     */
    private Double fallbackScore(Document document) {
        if (document.getScore() != null) {
            return document.getScore();
        }
        Object rrfScore = document.getMetadata().get(RrfDocumentJoiner.METADATA_RRF_SCORE);
        if (rrfScore instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return rrfScore == null ? 0D : Double.parseDouble(String.valueOf(rrfScore));
        } catch (NumberFormatException ex) {
            return 0D;
        }
    }

    /**
     * 将 Rerank 模型原始响应转换成 RerankedDocument。
     *
     * <p>为了兼容不同 Spring AI Alibaba 版本，这里通过反射读取 results / index / score 等字段。</p>
     */
    private List<RerankedDocument> convertResponse(Object rawResponse, List<Document> candidates) {
        Collection<?> responseResults = extractResultItems(rawResponse);
        if (responseResults.isEmpty()) {
            return List.of();
        }

        Map<String, Queue<Document>> candidatesByText = buildCandidatesByText(candidates);
        List<RerankedDocument> results = new ArrayList<>();
        for (Object item : responseResults) {
            Document document = resolveDocument(item, candidates, candidatesByText);
            Double score = resolveScore(item);
            if (document == null || score == null) {
                continue;
            }
            results.add(new RerankedDocument(document, normalizeScore(score)));
        }
        return results;
    }

    /**
     * 读取 Rerank 响应中的结果列表。
     */
    private Collection<?> extractResultItems(Object rawResponse) {
        Object value = invokeAny(rawResponse, "getResults", "results", "getResult", "result");
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return List.of();
    }

    /**
     * 将单条 Rerank 返回项映射回原始候选 Document。
     *
     * <p>优先按 index 映射；没有 index 时按文档文本映射。</p>
     */
    private Document resolveDocument(Object item, List<Document> candidates, Map<String, Queue<Document>> candidatesByText) {
        Integer index = resolveIndex(item);
        if (index != null && index >= 0 && index < candidates.size()) {
            return candidates.get(index);
        }

        String documentText = resolveDocumentText(item);
        if (!StringUtils.hasText(documentText)) {
            return null;
        }
        Queue<Document> sameTextCandidates = candidatesByText.get(documentText.trim());
        return sameTextCandidates == null ? null : sameTextCandidates.poll();
    }

    /**
     * 解析 Rerank 返回项中的候选下标。
     */
    private Integer resolveIndex(Object item) {
        Object value = invokeAny(item, "getIndex", "index", "getDocumentIndex", "documentIndex");
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 解析 Rerank 返回项中的相关性分数。
     */
    private Double resolveScore(Object item) {
        Object value = invokeAny(item, "getRelevanceScore", "relevanceScore", "getScore", "score");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 解析 Rerank 返回项中携带的文档文本。
     */
    private String resolveDocumentText(Object item) {
        Object document = invokeAny(item, "getDocument", "document", "getOutput", "output");
        if (document == null) {
            return null;
        }
        Object text = invokeAny(document, "getText", "text", "getContent", "content");
        return text == null ? null : String.valueOf(text);
    }

    /**
     * 建立“文档文本 -> 候选队列”的映射，处理重复文本时按出现顺序消费。
     */
    private Map<String, Queue<Document>> buildCandidatesByText(List<Document> candidates) {
        Map<String, Queue<Document>> result = new HashMap<>();
        for (Document candidate : candidates) {
            if (!StringUtils.hasText(candidate.getText())) {
                continue;
            }
            result.computeIfAbsent(candidate.getText().trim(), ignored -> new ArrayDeque<>()).offer(candidate);
        }
        return result;
    }

    /**
     * 依次尝试多个可能的方法名，兼容不同版本的 Rerank SDK 响应对象。
     */
    private Object invokeAny(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ex) {
                // Try the next method name to support different Spring AI Alibaba versions.
            }
        }
        return null;
    }

    /**
     * 将模型分数约束到 0-1 区间。
     */
    private Double normalizeScore(Double score) {
        if (score > 1D) {
            return 1D;
        }
        if (score < 0D) {
            return 0D;
        }
        return score;
    }

    /**
     * Rerank 后的文档包装对象。
     */
    private record RerankedDocument(Document document, Double score) {

        /**
         * 生成写入 rerankScore 的新 Document。
         */
        private Document toDocument() {
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put(METADATA_RERANK_SCORE, score);
            return document.mutate()
                    .metadata(metadata)
                    .score(score)
                    .build();
        }
    }
}
