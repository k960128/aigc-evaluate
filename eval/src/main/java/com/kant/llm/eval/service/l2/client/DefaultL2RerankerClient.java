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
 * <p>当前没有接入真实 Reranker 模型时，优先使用候选已有的 mock 精排分，再使用 RRF 分作为兜底，
 * 保留后续替换真实精排模型的接口边界。</p>
 */
@Component
public class DefaultL2RerankerClient implements L2RerankerClient {

    @Override
    public List<L2RerankResult> rerank(L2RerankRequest request) {
        if (request.getCandidates() == null || request.getCandidates().isEmpty()) {
            return List.of();
        }
        // 第一阶段没有真实 Reranker 服务，仍然保留 rerank 接口调用，
        // 是为了让 L2EvaluationService 的主流程和未来真实精排接入后的流程完全一致。
        return request.getCandidates().stream()
                .map(this::convertToRerankResult)
                .toList();
    }

    /**
     * 优先沿用召回侧预置的 mock 精排分；没有预置分时，将 RRF 分降级作为 Reranker 分。
     */
    private L2RerankResult convertToRerankResult(L2FeatureHit candidate) {
        BigDecimal fallbackScore = candidate.getRerankScore() == null
                ? candidate.getRrfScore()
                : candidate.getRerankScore();
        return L2RerankResult.builder()
                .featureId(candidate.getFeatureId())
                .rerankScore(fallbackScore == null ? BigDecimal.ZERO : fallbackScore)
                .build();
    }
}
