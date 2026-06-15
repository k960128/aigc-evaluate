package com.kant.llm.eval.service.l2.client;

import com.kant.llm.eval.common.condition.ConditionalOnL2RecallMode;
import com.kant.llm.eval.common.constant.EsDocumentChunk;
import com.kant.llm.eval.dao.entity.RiskCategoryDO;
import com.kant.llm.eval.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.dao.mapper.RiskCategoryMapper;
import com.kant.llm.eval.dao.mapper.RiskDetailRuleMapper;
import com.kant.llm.eval.dao.mapper.RiskDetailsMapper;
import com.kant.llm.eval.service.es.ElasticSearchService;
import com.kant.llm.eval.service.l2.model.L2FeatureHit;
import com.kant.llm.eval.service.l2.model.L2RecallRequest;
import com.kant.llm.eval.service.l2.model.L2RecallResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * L2 真实 ES + PGVector 混合召回客户端。
 *
 * <p>PGVector 命中仍写入 milvusHits / milvusSimilarity 字段，是为了兼容现有 L2 RRF 和流水线日志结构。
 * 真正用于 RRF 去重的字段始终是 L2FeatureHit.featureId，也就是 MySQL risk_attack_feature.id。</p>
 */
@Slf4j
@Component
@ConditionalOnL2RecallMode("real")
public class EsPgL2RecallClient implements L2RecallClient {

    private static final int DEFAULT_TOP_K = 30;

    private static final int SCORE_SCALE = 6;

    private static final String SOURCE_ES = "ES";

    private static final String SOURCE_VECTOR_COMPAT = "MILVUS";

    private static final String ACTIVE_FEATURE_FILTER = "status == 1";

    private final ElasticSearchService elasticSearchService;

    private final VectorStore vectorStore;

    private final RiskDetailsMapper riskDetailsMapper;

    private final RiskCategoryMapper riskCategoryMapper;

    private final RiskDetailRuleMapper riskDetailRuleMapper;

    public EsPgL2RecallClient(ElasticSearchService elasticSearchService,
                              @Qualifier("vectorStore") VectorStore vectorStore,
                              RiskDetailsMapper riskDetailsMapper,
                              RiskCategoryMapper riskCategoryMapper,
                              RiskDetailRuleMapper riskDetailRuleMapper) {
        this.elasticSearchService = elasticSearchService;
        this.vectorStore = vectorStore;
        this.riskDetailsMapper = riskDetailsMapper;
        this.riskCategoryMapper = riskCategoryMapper;
        this.riskDetailRuleMapper = riskDetailRuleMapper;
    }

    @Override
    public L2RecallResult recall(L2RecallRequest request) {
        if (request == null || !StringUtils.hasText(request.getQueryText())) {
            log.debug("L2 真实召回跳过：请求为空或 queryText 为空");
            return emptyResult(false);
        }
        String queryText = request.getQueryText();
        int esTopK = resolveTopK(request.getEsTopK());
        int vectorTopK = resolveTopK(request.getMilvusTopK());
        log.info("开始 L2 真实混合召回，esTopK: {}, pgVectorTopK: {}, queryLength: {}",
                esTopK, vectorTopK, queryText.length());

        boolean esFailed = false;
        boolean vectorFailed = false;
        List<EsDocumentChunk> esRawHits;
        List<Document> vectorRawHits;

        try {
            esRawHits = elasticSearchService.searchRiskAttackFeatures(queryText, esTopK);
            log.info("L2 ES 真实召回完成，rawHits: {}", esRawHits.size());
        } catch (Exception ex) {
            esFailed = true;
            esRawHits = List.of();
            log.warn("L2 ES 真实召回失败，将保留 PGVector 结果继续判定，reason: {}", ex.getMessage(), ex);
        }

        try {
            vectorRawHits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(queryText)
                    .topK(vectorTopK)
                    .filterExpression(ACTIVE_FEATURE_FILTER)
                    .build());
            if (vectorRawHits == null) {
                vectorRawHits = List.of();
            }
            vectorRawHits = vectorRawHits.stream()
                    .limit(vectorTopK)
                    .toList();
            log.info("L2 PGVector 真实召回完成，rawHits: {}", vectorRawHits.size());
        } catch (Exception ex) {
            vectorFailed = true;
            vectorRawHits = List.of();
            log.warn("L2 PGVector 真实召回失败，将保留 ES 结果继续判定，reason: {}", ex.getMessage(), ex);
        }

        boolean degraded = esFailed || vectorFailed;
        if (esRawHits.isEmpty() && vectorRawHits.isEmpty()) {
            log.info("L2 真实混合召回无命中，degraded: {}", degraded);
            return emptyResult(degraded);
        }

        LookupContext lookupContext = buildLookupContext(esRawHits, vectorRawHits);
        List<L2FeatureHit> esHits = toEsHits(esRawHits, lookupContext);
        List<L2FeatureHit> vectorHits = toVectorHits(vectorRawHits, lookupContext);
        log.info("L2 真实混合召回结果构造完成，esHits: {}, milvusHits(pgVector): {}, degraded: {}",
                esHits.size(), vectorHits.size(), degraded);
        return L2RecallResult.builder()
                .esHits(esHits)
                .milvusHits(vectorHits)
                .degraded(degraded)
                .build();
    }

    private List<L2FeatureHit> toEsHits(List<EsDocumentChunk> rawHits, LookupContext lookupContext) {
        java.util.ArrayList<L2FeatureHit> hits = new java.util.ArrayList<>();
        for (int i = 0; i < rawHits.size(); i++) {
            EsDocumentChunk rawHit = rawHits.get(i);
            if (rawHit.getFeatureId() == null) {
                continue;
            }
            L2FeatureHit hit = toEsHit(rawHit, lookupContext, i + 1);
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private L2FeatureHit toEsHit(EsDocumentChunk hit, LookupContext lookupContext, int rank) {
        RiskDetailsDO detail = getDetail(lookupContext, hit.getRiskDetailsId());
        RiskCategoryDO category = getCategory(lookupContext, hit.getCategoryId());
        BigDecimal esScore = hit.getNormalizedScore() == null ? BigDecimal.ZERO : hit.getNormalizedScore();
        return L2FeatureHit.builder()
                .featureId(hit.getFeatureId())
                .riskDetailsId(hit.getRiskDetailsId())
                .detailsName(detail == null ? null : detail.getDetailsName())
                .categoryId(hit.getCategoryId())
                .categoryName(category == null ? null : category.getCategoryName())
                .featureText(resolveEsFeatureText(hit))
                .featureType(hit.getFeatureType())
                .polarity(hit.getPolarity())
                .riskLevel(hit.getRiskLevel())
                .severityLevel(resolveSeverity(hit.getRiskDetailsId(), hit.getRiskLevel(), lookupContext))
                .weight(hit.getWeight())
                .sources(List.of(SOURCE_ES))
                .esRank(rank)
                .esScore(esScore)
                .rerankScore(esScore)
                .build();
    }

    private List<L2FeatureHit> toVectorHits(List<Document> rawHits, LookupContext lookupContext) {
        java.util.ArrayList<L2FeatureHit> hits = new java.util.ArrayList<>();
        for (int i = 0; i < rawHits.size(); i++) {
            L2FeatureHit hit = toVectorHit(rawHits.get(i), lookupContext, i + 1);
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private L2FeatureHit toVectorHit(Document document, LookupContext lookupContext, int rank) {
        Map<String, Object> metadata = safeMetadata(document);
        Long featureId = asLong(metadata.get("featureId"));
        if (featureId == null) {
            log.warn("L2 PGVector 命中缺少 metadata.featureId，跳过该条，documentId: {}", document.getId());
            return null;
        }
        Long riskDetailsId = asLong(metadata.get("riskDetailsId"));
        Long categoryId = asLong(metadata.get("categoryId"));
        Integer riskLevel = asInteger(metadata.get("riskLevel"));
        RiskDetailsDO detail = getDetail(lookupContext, riskDetailsId);
        RiskCategoryDO category = getCategory(lookupContext, categoryId);
        BigDecimal similarity = resolveVectorSimilarity(document);
        return L2FeatureHit.builder()
                .featureId(featureId)
                .riskDetailsId(riskDetailsId)
                .detailsName(detail == null ? null : detail.getDetailsName())
                .categoryId(categoryId)
                .categoryName(category == null ? null : category.getCategoryName())
                .featureText(document.getText())
                .featureType(asString(metadata.get("featureType")))
                .polarity(asString(metadata.get("polarity")))
                .riskLevel(riskLevel)
                .severityLevel(resolveSeverity(riskDetailsId, riskLevel, lookupContext))
                .weight(asBigDecimal(metadata.get("weight")))
                .sources(List.of(SOURCE_VECTOR_COMPAT))
                .milvusRank(rank)
                .milvusSimilarity(similarity)
                .rerankScore(similarity)
                .build();
    }

    private LookupContext buildLookupContext(List<EsDocumentChunk> esHits, List<Document> vectorHits) {
        Set<Long> detailIds = new LinkedHashSet<>();
        Set<Long> categoryIds = new LinkedHashSet<>();
        esHits.forEach(hit -> {
            addIfNotNull(detailIds, hit.getRiskDetailsId());
            addIfNotNull(categoryIds, hit.getCategoryId());
        });
        vectorHits.forEach(hit -> {
            Map<String, Object> metadata = safeMetadata(hit);
            addIfNotNull(detailIds, asLong(metadata.get("riskDetailsId")));
            addIfNotNull(categoryIds, asLong(metadata.get("categoryId")));
        });

        Map<Long, RiskDetailsDO> detailMap = detailIds.isEmpty()
                ? Map.of()
                : riskDetailsMapper.selectBatchIds(detailIds).stream()
                .collect(Collectors.toMap(RiskDetailsDO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        detailMap.values().stream()
                .map(RiskDetailsDO::getCategoryId)
                .filter(Objects::nonNull)
                .forEach(categoryIds::add);

        Map<Long, RiskCategoryDO> categoryMap = categoryIds.isEmpty()
                ? Map.of()
                : riskCategoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(RiskCategoryDO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        Map<Long, Integer> severityMap = detailIds.isEmpty()
                ? Map.of()
                : riskDetailRuleMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RiskDetailRuleDO>()
                        .in(RiskDetailRuleDO::getRiskDetailsId, detailIds)
                        .eq(RiskDetailRuleDO::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(RiskDetailRuleDO::getRiskDetailsId,
                        RiskDetailRuleDO::getSeverityLevel,
                        (left, right) -> left,
                        LinkedHashMap::new));
        log.debug("L2 真实召回上下文补齐完成，detailMapSize: {}, categoryMapSize: {}, severityMapSize: {}",
                detailMap.size(), categoryMap.size(), severityMap.size());
        return new LookupContext(detailMap, categoryMap, severityMap);
    }

    private String resolveEsFeatureText(EsDocumentChunk hit) {
        if (StringUtils.hasText(hit.getFeatureText())) {
            return hit.getFeatureText();
        }
        if (StringUtils.hasText(hit.getNormalizedText())) {
            return hit.getNormalizedText();
        }
        return hit.getContent();
    }

    private BigDecimal resolveVectorSimilarity(Document document) {
        Map<String, Object> metadata = safeMetadata(document);
        BigDecimal score = asBigDecimal(metadata.get("score"));
        if (score == null) {
            score = readDocumentScoreByReflection(document);
        }
        if (score == null) {
            score = asBigDecimal(metadata.get("similarity"));
        }
        if (score == null) {
            BigDecimal distance = asBigDecimal(metadata.get("distance"));
            if (distance != null) {
                score = BigDecimal.ONE.subtract(distance);
            }
        }
        if (score == null) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ONE) > 0) {
            score = BigDecimal.ONE;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    private Map<String, Object> safeMetadata(Document document) {
        return document.getMetadata() == null ? Map.of() : document.getMetadata();
    }

    private BigDecimal readDocumentScoreByReflection(Document document) {
        try {
            Method method = document.getClass().getMethod("getScore");
            return asBigDecimal(method.invoke(document));
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private Integer resolveSeverity(Long riskDetailsId, Integer riskLevel, LookupContext lookupContext) {
        if (riskDetailsId == null) {
            return riskLevel;
        }
        return lookupContext.severityMap().getOrDefault(riskDetailsId, riskLevel);
    }

    private RiskDetailsDO getDetail(LookupContext lookupContext, Long riskDetailsId) {
        return riskDetailsId == null ? null : lookupContext.detailMap().get(riskDetailsId);
    }

    private RiskCategoryDO getCategory(LookupContext lookupContext, Long categoryId) {
        return categoryId == null ? null : lookupContext.categoryMap().get(categoryId);
    }

    private int resolveTopK(Integer topK) {
        return topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
    }

    private void addIfNotNull(Set<Long> values, Long value) {
        if (value != null) {
            values.add(value);
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private L2RecallResult emptyResult(boolean degraded) {
        return L2RecallResult.builder()
                .esHits(List.of())
                .milvusHits(List.of())
                .degraded(degraded)
                .build();
    }

    private record LookupContext(Map<Long, RiskDetailsDO> detailMap,
                                 Map<Long, RiskCategoryDO> categoryMap,
                                 Map<Long, Integer> severityMap) {
    }
}
