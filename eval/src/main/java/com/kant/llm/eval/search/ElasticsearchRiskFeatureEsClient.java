package com.kant.llm.eval.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.kant.llm.eval.common.config.L2EsProperties;
import com.kant.llm.eval.common.condition.ConditionalOnL2RealSearchEnabled;
import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Elasticsearch Java API Client 的 L2 攻击特征检索客户端。
 *
 * <p>ES 在 L2 中只负责关键词/BM25 召回，不直接做最终安全判定。
 * 这里输出的分数会先归一化，再交给 L2 RRF 融合、rerank 和阈值路由继续处理。</p>
 */
@Slf4j
@Component
@ConditionalOnL2RealSearchEnabled
@RequiredArgsConstructor
public class ElasticsearchRiskFeatureEsClient implements RiskFeatureEsClient {

    private static final int DEFAULT_TOP_K = 30;

    private static final int SCORE_SCALE = 6;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ElasticsearchClient elasticsearchClient;

    private final L2EsProperties properties;

    @Override
    public List<RiskFeatureEsHit> search(String queryText, int topK) {
        if (StringUtils.isBlank(queryText)) {
            log.debug("L2 ES 召回跳过：查询文本为空");
            return List.of();
        }
        int limit = topK <= 0 ? DEFAULT_TOP_K : topK;
        log.info("开始 L2 ES 召回，index: {}, topK: {}, queryLength: {}",
                properties.getIndexName(), limit, queryText.length());
        try {
            SearchResponse<RiskFeatureEsDocument> response = elasticsearchClient.search(search -> search
                            .index(properties.getIndexName())
                            .size(limit)
                            .query(query -> query
                                    .bool(bool -> bool
                                            .filter(filter -> filter.term(term -> term
                                                    .field("status")
                                                    .value(1)))
                                            .must(must -> must.multiMatch(multiMatch -> multiMatch
                                                    .query(queryText)
                                                    .fields(
                                                            "featureText^3",
                                                            "normalizedText^3",
                                                            "featureCode^1.5",
                                                            "tags")
                                                    .operator(Operator.Or))))),
                    RiskFeatureEsDocument.class);
            double maxScore = response.hits().maxScore() == null || response.hits().maxScore() <= 0D
                    ? 1D
                    : response.hits().maxScore();
            List<RiskFeatureEsHit> hits = response.hits().hits().stream()
                    .map(hit -> toHit(hit, maxScore))
                    .filter(Objects::nonNull)
                    .toList();
            log.info("L2 ES 召回完成，index: {}, hits: {}, maxScore: {}",
                    properties.getIndexName(), hits.size(), maxScore);
            return hits;
        } catch (IOException | ElasticsearchException ex) {
            log.warn("L2 ES 召回异常，index: {}, topK: {}, queryLength: {}, reason: {}",
                    properties.getIndexName(), limit, queryText.length(), ex.getMessage(), ex);
            throw new ServiceException("L2 ES 召回失败：" + ex.getMessage(), ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    @Override
    public void upsertFeature(RiskAttackFeatureDO feature) {
        validateFeature(feature);
        RiskFeatureEsDocument document = toDocument(feature);
        log.debug("准备写入 L2 ES 特征，index: {}, featureId: {}, riskDetailsId: {}, contentHash: {}, version: {}, status: {}",
                properties.getIndexName(), feature.getId(), feature.getRiskDetailsId(),
                feature.getContentHash(), feature.getVersion(), feature.getStatus());
        try {
            elasticsearchClient.index(IndexRequest.of(request -> request
                    .index(properties.getIndexName())
                    .id(String.valueOf(feature.getId()))
                    .document(document)));
            log.info("L2 ES 特征写入完成，index: {}, featureId: {}, riskDetailsId: {}, version: {}",
                    properties.getIndexName(), feature.getId(), feature.getRiskDetailsId(), feature.getVersion());
        } catch (IOException | ElasticsearchException ex) {
            log.warn("L2 ES 特征写入异常，index: {}, featureId: {}, reason: {}",
                    properties.getIndexName(), feature.getId(), ex.getMessage(), ex);
            throw new ServiceException("L2 ES 特征写入失败：" + ex.getMessage(), ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    @Override
    public void deleteFeature(Long featureId) {
        if (featureId == null) {
            log.debug("L2 ES 特征删除跳过：featureId 为空");
            return;
        }
        log.debug("准备删除 L2 ES 特征，index: {}, featureId: {}", properties.getIndexName(), featureId);
        try {
            elasticsearchClient.delete(DeleteRequest.of(request -> request
                    .index(properties.getIndexName())
                    .id(String.valueOf(featureId))));
            log.info("L2 ES 特征删除完成，index: {}, featureId: {}", properties.getIndexName(), featureId);
        } catch (ElasticsearchException ex) {
            if (ex.status() == 404) {
                log.info("L2 ES 特征不存在，按幂等删除处理，index: {}, featureId: {}",
                        properties.getIndexName(), featureId);
                return;
            }
            log.warn("L2 ES 特征删除异常，index: {}, featureId: {}, reason: {}",
                    properties.getIndexName(), featureId, ex.getMessage(), ex);
            throw new ServiceException("L2 ES 特征删除失败：" + ex.getMessage(), ex, BaseErrorCode.REMOTE_ERROR);
        } catch (IOException ex) {
            log.warn("L2 ES 特征删除 IO 异常，index: {}, featureId: {}, reason: {}",
                    properties.getIndexName(), featureId, ex.getMessage(), ex);
            throw new ServiceException("L2 ES 特征删除失败：" + ex.getMessage(), ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private RiskFeatureEsHit toHit(Hit<RiskFeatureEsDocument> hit, double maxScore) {
        RiskFeatureEsDocument document = hit.source();
        if (document == null || document.getFeatureId() == null) {
            return null;
        }
        double rawScore = hit.score() == null ? 0D : hit.score();
        // ES BM25 原始分只在同一次查询内可比较，这里按本次 maxScore 压到 0-1，便于和 PGVector 相似度一起进入 L2 融合。
        BigDecimal normalizedScore = BigDecimal.valueOf(rawScore / maxScore)
                .min(BigDecimal.ONE)
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        return RiskFeatureEsHit.builder()
                .featureId(document.getFeatureId())
                .riskDetailsId(document.getRiskDetailsId())
                .categoryId(document.getCategoryId())
                .featureText(document.getFeatureText())
                .featureType(document.getFeatureType())
                .polarity(document.getPolarity())
                .riskLevel(document.getRiskLevel())
                .weight(document.getWeight())
                .contentHash(document.getContentHash())
                .rawScore(rawScore)
                .normalizedScore(normalizedScore)
                .build();
    }

    private RiskFeatureEsDocument toDocument(RiskAttackFeatureDO feature) {
        return RiskFeatureEsDocument.builder()
                .featureId(feature.getId())
                .riskDetailsId(feature.getRiskDetailsId())
                .categoryId(feature.getCategoryId())
                .featureCode(feature.getFeatureCode())
                .featureText(feature.getFeatureText())
                .normalizedText(feature.getNormalizedText())
                .featureType(feature.getFeatureType())
                .polarity(feature.getPolarity())
                .riskLevel(feature.getRiskLevel())
                .language(feature.getLanguage())
                .tags(feature.getTags())
                .source(feature.getSource())
                .weight(feature.getWeight())
                .contentHash(feature.getContentHash())
                .version(feature.getVersion())
                .status(feature.getStatus())
                .createTime(formatDateTime(feature.getCreateTime()))
                .updateTime(formatDateTime(feature.getUpdateTime()))
                .build();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }

    private void validateFeature(RiskAttackFeatureDO feature) {
        if (feature == null || feature.getId() == null) {
            throw new ServiceException("L2 ES 特征 ID 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        if (StringUtils.isBlank(feature.getFeatureText())) {
            throw new ServiceException("L2 ES 特征文本不能为空", BaseErrorCode.CLIENT_ERROR);
        }
    }
}
