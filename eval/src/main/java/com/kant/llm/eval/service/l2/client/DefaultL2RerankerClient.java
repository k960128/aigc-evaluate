package com.kant.llm.eval.service.l2.client;

import com.kant.llm.eval.service.l2.model.L2FeatureHit;
import com.kant.llm.eval.service.l2.model.L2RerankRequest;
import com.kant.llm.eval.service.l2.model.L2RerankResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * L2 Reranker 降级客户端。
 *
 * <p>当前没有接入真实 Reranker 模型时，使用候选已有的 RRF 分作为精排分，
 * 保留后续替换真实精排模型的接口边界。</p>
 */
@Component
public class DefaultL2RerankerClient implements L2RerankerClient {

    @Override
    public List<L2RerankResult> rerank(L2RerankRequest request) {
        if (request.getCandidates() == null || request.getCandidates().isEmpty()) {
            return List.of();
        }
        return request.getCandidates().stream()
                .map(this::convertToRerankResult)
                .toList();
    }

    /**
     * 将 RRF 分降级作为 Reranker 分。
     */
    private L2RerankResult convertToRerankResult(L2FeatureHit candidate) {
        BigDecimal fallbackScore = candidate.getRrfScore() == null ? BigDecimal.ZERO : candidate.getRrfScore();
        return L2RerankResult.builder()
                .featureId(candidate.getFeatureId())
                .rerankScore(fallbackScore)
                .build();
    }
}
