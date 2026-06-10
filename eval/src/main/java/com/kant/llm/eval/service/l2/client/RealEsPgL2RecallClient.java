package com.kant.llm.eval.service.l2.client;

import com.kant.llm.eval.common.condition.ConditionalOnL2RecallMode;
import com.kant.llm.eval.common.config.L2RecallModeProperties.RecallMode;
import com.kant.llm.eval.dao.entity.RiskCategoryDO;
import com.kant.llm.eval.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.dao.mapper.RiskCategoryMapper;
import com.kant.llm.eval.dao.mapper.RiskDetailRuleMapper;
import com.kant.llm.eval.dao.mapper.RiskDetailsMapper;
import com.kant.llm.eval.search.RiskFeatureEsClient;
import com.kant.llm.eval.search.RiskFeatureEsHit;
import com.kant.llm.eval.service.l2.model.L2FeatureHit;
import com.kant.llm.eval.service.l2.model.L2RecallRequest;
import com.kant.llm.eval.service.l2.model.L2RecallResult;
import com.kant.llm.eval.vector.RiskFeatureVectorHit;
import com.kant.llm.eval.vector.RiskFeatureVectorStoreClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * L2 真实 ES + PGVector 双路召回客户端。
 *
 * <p>本类只负责“召回”和“补齐展示字段”，最终是否拦截仍由 L2EvaluationService 的融合、聚合和阈值路由决定。
 * 为了兼容已有流水线日志字段，PGVector 命中仍写入 milvusHits，来源标记也暂时沿用 MILVUS。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnL2RecallMode(mode = RecallMode.REAL)
public class RealEsPgL2RecallClient implements L2RecallClient {

    private static final int SCORE_SCALE = 6;

    private final RiskFeatureEsClient riskFeatureEsClient;

    private final RiskFeatureVectorStoreClient riskFeatureVectorStoreClient;

    private final RiskDetailsMapper riskDetailsMapper;

    private final RiskCategoryMapper riskCategoryMapper;

    private final RiskDetailRuleMapper riskDetailRuleMapper;

    @Override
    public L2RecallResult recall(L2RecallRequest request) {
        if (request == null || StringUtils.isBlank(request.getQueryText())) {
            log.debug("L2 真实召回跳过：请求为空或 queryText 为空");
            return emptyResult(false);
        }
        log.info("开始 L2 真实召回，esTopK: {}, pgVectorTopK: {}, queryLength: {}",
                request.getEsTopK(), request.getMilvusTopK(), request.getQueryText().length());

        boolean degraded = false;
        List<RiskFeatureEsHit> esRawHits;
        List<RiskFeatureVectorHit> vectorRawHits;

        try {
            esRawHits = riskFeatureEsClient.search(request.getQueryText(), request.getEsTopK());
            log.debug("L2 ES 真实召回原始命中完成，rawHits: {}", esRawHits.size());
        } catch (Exception ex) {
            degraded = true;
            esRawHits = List.of();
            log.warn("L2 ES 真实召回失败，将保留 PGVector 结果继续判定，reason: {}", ex.getMessage(), ex);
        }

        try {
            vectorRawHits = riskFeatureVectorStoreClient.similaritySearch(request.getQueryText(), request.getMilvusTopK());
            log.debug("L2 PGVector 真实召回原始命中完成，rawHits: {}", vectorRawHits.size());
        } catch (Exception ex) {
            degraded = true;
            vectorRawHits = List.of();
            log.warn("L2 PGVector 真实召回失败，将保留 ES 结果继续判定，reason: {}", ex.getMessage(), ex);
        }

        if (esRawHits.isEmpty() && vectorRawHits.isEmpty()) {
            log.info("L2 真实召回无命中，degraded: {}", degraded);
            return emptyResult(degraded);
        }

        LookupContext lookupContext = buildLookupContext(esRawHits, vectorRawHits);
        List<L2FeatureHit> esHits = toEsHits(esRawHits, lookupContext);
        List<L2FeatureHit> vectorHits = toVectorHits(vectorRawHits, lookupContext);
        log.info("L2 真实召回完成，esHits: {}, pgVectorHits: {}, degraded: {}",
                esHits.size(), vectorHits.size(), degraded);
        return L2RecallResult.builder()
                .esHits(esHits)
                .milvusHits(vectorHits)
                .degraded(degraded)
                .build();
    }

    private List<L2FeatureHit> toEsHits(List<RiskFeatureEsHit> rawHits, LookupContext lookupContext) {
        return rawHits.stream()
                .sorted(Comparator.comparing(RiskFeatureEsHit::getNormalizedScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(hit -> toEsHit(hit, lookupContext))
                .toList();
    }

    private L2FeatureHit toEsHit(RiskFeatureEsHit hit, LookupContext lookupContext) {
        RiskDetailsDO detail = getDetail(lookupContext, hit.getRiskDetailsId());
        RiskCategoryDO category = getCategory(lookupContext, hit.getCategoryId());
        return L2FeatureHit.builder()
                .featureId(hit.getFeatureId())
                .riskDetailsId(hit.getRiskDetailsId())
                .detailsName(detail == null ? null : detail.getDetailsName())
                .categoryId(hit.getCategoryId())
                .categoryName(category == null ? null : category.getCategoryName())
                .featureText(hit.getFeatureText())
                .featureType(hit.getFeatureType())
                .polarity(hit.getPolarity())
                .riskLevel(hit.getRiskLevel())
                .severityLevel(resolveSeverity(hit.getRiskDetailsId(), hit.getRiskLevel(), lookupContext))
                .weight(hit.getWeight())
                .sources(List.of("ES"))
                .esScore(hit.getNormalizedScore())
                .rerankScore(hit.getNormalizedScore())
                .build();
    }

    private List<L2FeatureHit> toVectorHits(List<RiskFeatureVectorHit> rawHits, LookupContext lookupContext) {
        return rawHits.stream()
                .sorted(Comparator.comparing(RiskFeatureVectorHit::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(hit -> toVectorHit(hit, lookupContext))
                .toList();
    }

    private L2FeatureHit toVectorHit(RiskFeatureVectorHit hit, LookupContext lookupContext) {
        RiskDetailsDO detail = getDetail(lookupContext, hit.getRiskDetailsId());
        RiskCategoryDO category = getCategory(lookupContext, hit.getCategoryId());
        BigDecimal similarity = normalizeVectorScore(hit.getScore());
        // 兼容既有 L2 日志结构：PGVector 相似度暂时写入 milvusSimilarity，sources 仍使用 MILVUS。
        return L2FeatureHit.builder()
                .featureId(hit.getFeatureId())
                .riskDetailsId(hit.getRiskDetailsId())
                .detailsName(detail == null ? null : detail.getDetailsName())
                .categoryId(hit.getCategoryId())
                .categoryName(category == null ? null : category.getCategoryName())
                .featureText(hit.getFeatureText())
                .featureType(hit.getFeatureType())
                .polarity(hit.getPolarity())
                .riskLevel(hit.getRiskLevel())
                .severityLevel(resolveSeverity(hit.getRiskDetailsId(), hit.getRiskLevel(), lookupContext))
                .weight(hit.getWeight())
                .sources(List.of("MILVUS"))
                .milvusSimilarity(similarity)
                .rerankScore(similarity)
                .build();
    }

    private LookupContext buildLookupContext(List<RiskFeatureEsHit> esHits, List<RiskFeatureVectorHit> vectorHits) {
        Set<Long> detailIds = new LinkedHashSet<>();
        Set<Long> categoryIds = new LinkedHashSet<>();
        esHits.forEach(hit -> {
            addIfNotNull(detailIds, hit.getRiskDetailsId());
            addIfNotNull(categoryIds, hit.getCategoryId());
        });
        vectorHits.forEach(hit -> {
            addIfNotNull(detailIds, hit.getRiskDetailsId());
            addIfNotNull(categoryIds, hit.getCategoryId());
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
        log.debug("L2 真实召回补齐上下文完成，detailIds: {}, categoryIds: {}, detailMapSize: {}, categoryMapSize: {}, severityMapSize: {}",
                detailIds.size(), categoryIds.size(), detailMap.size(), categoryMap.size(), severityMap.size());
        return new LookupContext(detailMap, categoryMap, severityMap);
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

    private BigDecimal normalizeVectorScore(Double score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(score)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE)
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    private void addIfNotNull(Set<Long> values, Long value) {
        if (value != null) {
            values.add(value);
        }
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
