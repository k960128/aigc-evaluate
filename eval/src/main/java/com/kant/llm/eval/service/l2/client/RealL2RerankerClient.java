package com.kant.llm.eval.service.l2.client;

import com.kant.ai.infra.rerank.RerankService;
import com.kant.ai.infra.rerank.RerankServiceResult;
import com.kant.llm.eval.common.condition.ConditionalOnL2RecallMode;
import com.kant.llm.eval.service.l2.model.L2FeatureHit;
import com.kant.llm.eval.service.l2.model.L2RerankRequest;
import com.kant.llm.eval.service.l2.model.L2RerankResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * L2 真实 Reranker 精排客户端。
 *
 * <p>仅在真实 ES + PGVector 召回模式下启用，调用 infra-ai 的通用 RerankService。
 * 如果模型调用失败、响应降级或 SDK 响应结构无法解析，则回退到默认降级分数，避免阻断 L2 主流程。</p>
 */
@Slf4j
@Primary
@Component
@ConditionalOnL2RecallMode("real")
public class RealL2RerankerClient implements L2RerankerClient {

    private static final int SCORE_SCALE = 6;

    private final RerankService rerankService;

    private final DefaultL2RerankerClient fallbackClient;

    public RealL2RerankerClient(RerankService rerankService) {
        this.rerankService = rerankService;
        this.fallbackClient = new DefaultL2RerankerClient();
    }

    @Override
    public List<L2RerankResult> rerank(L2RerankRequest request) {
        if (request == null) {
            return List.of();
        }
        if (request.getCandidates() == null || request.getCandidates().isEmpty()
                || StringUtils.isBlank(request.getQueryText())) {
            return fallbackClient.rerank(request);
        }

        List<L2FeatureHit> rerankCandidates = request.getCandidates().stream()
                .filter(candidate -> candidate != null && StringUtils.isNotBlank(candidate.getFeatureText()))
                .toList();
        if (rerankCandidates.isEmpty()) {
            log.warn("L2 真实 Reranker 跳过：候选特征文本为空，使用降级分数。");
            return fallbackClient.rerank(request);
        }

        List<String> documents = rerankCandidates.stream()
                .map(L2FeatureHit::getFeatureText)
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .toList();
        RerankServiceResult serviceResult = rerankService.rerank(request.getQueryText(), documents);
        if (serviceResult == null || Boolean.TRUE.equals(serviceResult.getDegraded()) || serviceResult.getRawResponse() == null) {
            log.warn("L2 真实 Reranker 降级：reason: {}",
                    serviceResult == null ? "RerankServiceResult 为空" : serviceResult.getReason());
            return fallbackClient.rerank(request);
        }

        List<L2RerankResult> results = convertResponse(serviceResult.getRawResponse(), rerankCandidates);
        if (results.isEmpty()) {
            log.warn("L2 真实 Reranker 响应无法映射到候选特征，使用降级分数。");
            return fallbackClient.rerank(request);
        }
        log.info("L2 真实 Reranker 精排完成，candidateSize: {}, resultSize: {}, model: {}, elapsed: {}ms",
                rerankCandidates.size(), results.size(), serviceResult.getModel(), serviceResult.getElapsed());
        return results;
    }

    private List<L2RerankResult> convertResponse(Object rawResponse, List<L2FeatureHit> candidates) {
        Collection<?> responseResults = extractResultItems(rawResponse);
        if (responseResults.isEmpty()) {
            return List.of();
        }

        Map<String, Queue<L2FeatureHit>> candidatesByText = buildCandidatesByText(candidates);
        List<L2RerankResult> results = new ArrayList<>();
        for (Object item : responseResults) {
            L2FeatureHit candidate = resolveCandidate(item, candidates, candidatesByText);
            BigDecimal score = resolveScore(item);
            if (candidate == null || candidate.getFeatureId() == null || score == null) {
                continue;
            }
            results.add(L2RerankResult.builder()
                    .featureId(candidate.getFeatureId())
                    .rerankScore(normalizeScore(score))
                    .build());
        }
        return results;
    }

    private Collection<?> extractResultItems(Object rawResponse) {
        Object value = invokeAny(rawResponse, "getResults", "results", "getResult", "result");
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return List.of();
    }

    private L2FeatureHit resolveCandidate(Object item,
                                          List<L2FeatureHit> candidates,
                                          Map<String, Queue<L2FeatureHit>> candidatesByText) {
        Integer index = resolveIndex(item);
        if (index != null && index >= 0 && index < candidates.size()) {
            return candidates.get(index);
        }

        String documentText = resolveDocumentText(item);
        if (StringUtils.isBlank(documentText)) {
            return null;
        }
        Queue<L2FeatureHit> sameTextCandidates = candidatesByText.get(documentText.trim());
        return sameTextCandidates == null ? null : sameTextCandidates.poll();
    }

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

    private BigDecimal resolveScore(Object item) {
        Object value = invokeAny(item, "getRelevanceScore", "relevanceScore", "getScore", "score");
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return value == null ? null : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveDocumentText(Object item) {
        Object document = invokeAny(item, "getDocument", "document", "getOutput", "output");
        if (document == null) {
            return null;
        }
        Object text = invokeAny(document, "getText", "text", "getContent", "content");
        return text == null ? null : String.valueOf(text);
    }

    private Map<String, Queue<L2FeatureHit>> buildCandidatesByText(List<L2FeatureHit> candidates) {
        Map<String, Queue<L2FeatureHit>> result = new HashMap<>();
        for (L2FeatureHit candidate : candidates) {
            if (StringUtils.isBlank(candidate.getFeatureText())) {
                continue;
            }
            result.computeIfAbsent(candidate.getFeatureText().trim(), ignored -> new ArrayDeque<>())
                    .offer(candidate);
        }
        return result;
    }

    private Object invokeAny(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ex) {
                // 尝试下一个候选方法名，兼容不同 Spring AI Alibaba 版本的响应对象。
            }
        }
        return null;
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score.compareTo(BigDecimal.ONE) > 0) {
            score = BigDecimal.ONE;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
