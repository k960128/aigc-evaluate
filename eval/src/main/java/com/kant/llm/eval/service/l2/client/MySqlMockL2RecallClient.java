package com.kant.llm.eval.service.l2.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.common.enums.RiskFeaturePolarityEnums;
import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;
import com.kant.llm.eval.dao.entity.RiskCategoryDO;
import com.kant.llm.eval.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.dao.mapper.RiskAttackFeatureMapper;
import com.kant.llm.eval.dao.mapper.RiskCategoryMapper;
import com.kant.llm.eval.dao.mapper.RiskDetailRuleMapper;
import com.kant.llm.eval.dao.mapper.RiskDetailsMapper;
import com.kant.llm.eval.service.l2.model.L2FeatureHit;
import com.kant.llm.eval.service.l2.model.L2RecallRequest;
import com.kant.llm.eval.service.l2.model.L2RecallResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * L2 MySQL Mock 召回客户端。
 *
 * <p>用于在 ES、Milvus、Reranker 尚未真实接入时，从 MySQL 知识库事实源中做轻量文本召回，
 * 让 L2 高危拦截、低风险放行和人工核验分支都可以被本地验证。</p>
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class MySqlMockL2RecallClient implements L2RecallClient {

    /** 请求未传 topK 时使用的默认召回数量，保持和 L2 默认阈值一致。 */
    private static final int DEFAULT_TOP_K = 30;

    /** 所有 mock 分数统一保留 6 位小数，便于和 RRF/rerank 日志对齐。 */
    private static final int SCORE_SCALE = 6;

    /** 低于该分数的特征认为只是弱相关，不进入 ES/Milvus mock 命中列表。 */
    private static final BigDecimal MIN_RECALL_SCORE = new BigDecimal("0.65");

    /** Mock 分数上限，避免权重放大后出现超过 1 的“相似度”。 */
    private static final BigDecimal MAX_SCORE = new BigDecimal("0.99");

    /** 中英文常见标点分隔器，用于把 feature_text 拆成轻量关键词。 */
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,，。.!！?？;；:：、/|()（）\\[\\]【】\"'`]+");

    private final RiskAttackFeatureMapper riskAttackFeatureMapper;

    private final RiskDetailsMapper riskDetailsMapper;

    private final RiskCategoryMapper riskCategoryMapper;

    private final RiskDetailRuleMapper riskDetailRuleMapper;

    @Override
    public L2RecallResult recall(L2RecallRequest request) {
        try {
            // 本客户端的定位是“无 ES/Milvus 时的本地验证适配器”，因此直接读取 MySQL 事实源。
            // 真实召回接入后，可以保留该类作为 dev/mock profile，生产环境切换到真实客户端。
            List<RiskAttackFeatureDO> features = riskAttackFeatureMapper.selectList(
                    new LambdaQueryWrapper<RiskAttackFeatureDO>()
                            .eq(RiskAttackFeatureDO::getStatus, 1)
                            .orderByAsc(RiskAttackFeatureDO::getRiskDetailsId)
                            .orderByAsc(RiskAttackFeatureDO::getId));
            if (features == null || features.isEmpty()) {
                return emptyResult(false);
            }

            Map<Long, RiskDetailsDO> detailMap = loadDetailMap(features);
            Map<Long, RiskCategoryDO> categoryMap = loadCategoryMap(features);
            Map<Long, Integer> severityMap = loadSeverityMap(features);
            // 评分逻辑只做轻量文本匹配，目标不是替代真实召回，而是让种子数据能稳定触发 L2 分支。
            // 排序优先级：匹配分 > 风险等级 > featureId，保证高风险强命中排在前面。
            List<ScoredFeature> scoredFeatures = features.stream()
                    .map(feature -> scoreFeature(request.getQueryText(), feature, detailMap.get(feature.getRiskDetailsId())))
                    .filter(Objects::nonNull)
                    .filter(scored -> scored.score().compareTo(MIN_RECALL_SCORE) >= 0)
                    .sorted(Comparator.comparing(ScoredFeature::score, Comparator.reverseOrder())
                            .thenComparing(scored -> nullToZero(scored.feature().getRiskLevel()), Comparator.reverseOrder())
                            .thenComparing(scored -> nullToZero(scored.feature().getId())))
                    .toList();
            if (scoredFeatures.isEmpty()) {
                return emptyResult(false);
            }

            // 同一批 scoredFeatures 同时构造成 ES 和 Milvus 两路结果：
            // ES 使用原始 mock 分，Milvus 略低 0.02，用来模拟“字面命中强、语义命中相近”的常见形态。
            // 后续 RRF 会按 featureId 合并两路来源。
            List<L2FeatureHit> esHits = buildHits(scoredFeatures, detailMap, categoryMap, severityMap, true, request.getEsTopK());
            List<L2FeatureHit> milvusHits = buildHits(scoredFeatures, detailMap, categoryMap, severityMap, false, request.getMilvusTopK());
            log.info("L2 MySQL Mock 召回完成，features: {}, esHits: {}, milvusHits: {}",
                    features.size(), esHits.size(), milvusHits.size());
            return L2RecallResult.builder()
                    .esHits(esHits)
                    .milvusHits(milvusHits)
                    .degraded(false)
                    .build();
        } catch (Exception ex) {
            // Mock 召回不能影响主评测链路可用性。数据库字段不兼容、种子数据未执行等异常统一降级为空召回。
            log.warn("L2 MySQL Mock 召回异常，降级为空召回，原因: {}", ex.getMessage(), ex);
            return emptyResult(true);
        }
    }

    /**
     * 批量加载风险小类信息，用于补全 L2 日志中的 detailsName。
     */
    private Map<Long, RiskDetailsDO> loadDetailMap(List<RiskAttackFeatureDO> features) {
        Set<Long> detailIds = features.stream()
                .map(RiskAttackFeatureDO::getRiskDetailsId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (detailIds.isEmpty()) {
            return Map.of();
        }
        return riskDetailsMapper.selectBatchIds(detailIds).stream()
                .collect(Collectors.toMap(RiskDetailsDO::getId, detail -> detail, (left, right) -> left, LinkedHashMap::new));
    }

    /**
     * 批量加载风险大类信息，用于补全 L2 日志中的 categoryName。
     */
    private Map<Long, RiskCategoryDO> loadCategoryMap(List<RiskAttackFeatureDO> features) {
        Set<Long> categoryIds = features.stream()
                .map(RiskAttackFeatureDO::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return riskCategoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(RiskCategoryDO::getId, category -> category, (left, right) -> left, LinkedHashMap::new));
    }

    /**
     * 批量加载风险小类严重等级。
     *
     * <p>risk_attack_feature 中有 riskLevel，risk_detail_rule 中有 severityLevel。
     * Mock 召回优先带出规则表的 severityLevel，缺失时再退回 feature 的 riskLevel。</p>
     */
    private Map<Long, Integer> loadSeverityMap(List<RiskAttackFeatureDO> features) {
        Set<Long> detailIds = features.stream()
                .map(RiskAttackFeatureDO::getRiskDetailsId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (detailIds.isEmpty()) {
            return Map.of();
        }
        return riskDetailRuleMapper.selectList(new LambdaQueryWrapper<RiskDetailRuleDO>()
                        .in(RiskDetailRuleDO::getRiskDetailsId, detailIds)
                        .eq(RiskDetailRuleDO::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(RiskDetailRuleDO::getRiskDetailsId,
                        RiskDetailRuleDO::getSeverityLevel,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    /**
     * 对单条风险特征计算 mock 召回分。
     *
     * <p>匹配来源包括 feature_text/normalized_text 和 risk_details.details_name。
     * 这样测试样本写“小类名称”或写具体特征词，都有机会被召回。</p>
     */
    private ScoredFeature scoreFeature(String queryText, RiskAttackFeatureDO feature, RiskDetailsDO detail) {
        String query = normalize(queryText);
        String compactQuery = compact(queryText);
        if (StringUtils.isBlank(query) && StringUtils.isBlank(compactQuery)) {
            return null;
        }

        String featureText = StringUtils.defaultIfBlank(feature.getNormalizedText(), feature.getFeatureText());
        List<String> tokens = tokenize(featureText);
        if (detail != null && StringUtils.isNotBlank(detail.getDetailsName())) {
            tokens = new ArrayList<>(tokens);
            tokens.addAll(tokenize(detail.getDetailsName()));
        }
        List<String> distinctTokens = tokens.stream()
                .map(this::normalize)
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
        if (distinctTokens.isEmpty()) {
            return null;
        }

        String compactFeatureText = compact(featureText);
        // phraseMatched 用去标点后的整体包含判断，适配中文短语中间没有空格的情况。
        boolean phraseMatched = StringUtils.isNotBlank(compactFeatureText) && compactQuery.contains(compactFeatureText);
        // token 命中用于适配测试文本只覆盖部分关键词的情况，例如“爆炸物 材料 清单”命中特征。
        int matchedCount = (int) distinctTokens.stream().filter(query::contains).count();
        BigDecimal baseScore = calculateBaseScore(phraseMatched, matchedCount, distinctTokens.size(), feature);
        if (baseScore.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        // weight 来自知识库特征权重，便于后续在 mock 数据中微调某些特征的召回强度。
        BigDecimal weightedScore = baseScore.multiply(nullToOne(feature.getWeight())).min(MAX_SCORE);
        return new ScoredFeature(feature, weightedScore.setScale(SCORE_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 根据匹配强度给出基础召回分。
     *
     * <p>整体短语命中最高；多关键词命中次之；安全例外允许单关键词弱命中，
     * 便于验证“拒答/科普”场景的降误报效果。</p>
     */
    private BigDecimal calculateBaseScore(boolean phraseMatched,
                                          int matchedCount,
                                          int tokenCount,
                                          RiskAttackFeatureDO feature) {
        if (phraseMatched) {
            return new BigDecimal("0.96");
        }
        if (matchedCount <= 0) {
            return BigDecimal.ZERO;
        }
        if (matchedCount >= tokenCount && tokenCount >= 2) {
            return new BigDecimal("0.92");
        }
        if (matchedCount >= 3) {
            return new BigDecimal("0.88");
        }
        if (matchedCount == 2) {
            return new BigDecimal("0.78");
        }
        if (RiskFeaturePolarityEnums.SAFE_EXCEPTION.getCode().equals(feature.getPolarity())) {
            return new BigDecimal("0.68");
        }
        return BigDecimal.ZERO;
    }

    /**
     * 将已评分特征转换成单路召回列表。
     */
    private List<L2FeatureHit> buildHits(List<ScoredFeature> scoredFeatures,
                                         Map<Long, RiskDetailsDO> detailMap,
                                         Map<Long, RiskCategoryDO> categoryMap,
                                         Map<Long, Integer> severityMap,
                                         boolean esHit,
                                         Integer topK) {
        int limit = topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
        List<L2FeatureHit> hits = new ArrayList<>();
        for (int i = 0; i < scoredFeatures.size() && hits.size() < limit; i++) {
            ScoredFeature scoredFeature = scoredFeatures.get(i);
            int rank = hits.size() + 1;
            hits.add(buildHit(scoredFeature, detailMap, categoryMap, severityMap, esHit, rank));
        }
        return hits;
    }

    /**
     * 构造 L2 标准命中对象。
     *
     * <p>这里预置 rerankScore，是为了让默认 Reranker 在 Mock 场景下保留较高置信分。
     * 否则仅使用 RRF 小分时，高危样本通常只会进入模糊区，无法验证高置信拦截。</p>
     */
    private L2FeatureHit buildHit(ScoredFeature scoredFeature,
                                  Map<Long, RiskDetailsDO> detailMap,
                                  Map<Long, RiskCategoryDO> categoryMap,
                                  Map<Long, Integer> severityMap,
                                  boolean esHit,
                                  int rank) {
        RiskAttackFeatureDO feature = scoredFeature.feature();
        RiskDetailsDO detail = detailMap.get(feature.getRiskDetailsId());
        RiskCategoryDO category = categoryMap.get(feature.getCategoryId());
        BigDecimal esScore = scoredFeature.score();
        BigDecimal milvusSimilarity = scoredFeature.score().subtract(new BigDecimal("0.02")).max(BigDecimal.ZERO)
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        BigDecimal rerankScore = esScore.max(milvusSimilarity);
        return L2FeatureHit.builder()
                .featureId(feature.getId())
                .riskDetailsId(feature.getRiskDetailsId())
                .detailsName(detail == null ? null : detail.getDetailsName())
                .categoryId(feature.getCategoryId())
                .categoryName(category == null ? null : category.getCategoryName())
                .featureText(feature.getFeatureText())
                .featureType(feature.getFeatureType())
                .polarity(feature.getPolarity())
                .riskLevel(feature.getRiskLevel())
                .severityLevel(severityMap.getOrDefault(feature.getRiskDetailsId(), feature.getRiskLevel()))
                .weight(feature.getWeight())
                .sources(List.of(esHit ? "ES" : "MILVUS"))
                .esRank(esHit ? rank : null)
                .esScore(esHit ? esScore : null)
                .milvusRank(esHit ? null : rank)
                .milvusSimilarity(esHit ? null : milvusSimilarity)
                .rerankScore(rerankScore)
                .build();
    }

    /**
     * 按常见中英文标点拆分文本，保留 2 个字符及以上 token 给评分逻辑使用。
     */
    private List<String> tokenize(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        String[] parts = SPLIT_PATTERN.split(text);
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /**
     * 统一大小写，主要兼容 mixed/en-US 特征，例如 DAN、Jailbreak。
     */
    private String normalize(String value) {
        return StringUtils.defaultString(value).toLowerCase(Locale.ROOT);
    }

    /**
     * 去除标点和空白，解决中文短语是否带空格都会匹配的问题。
     */
    private String compact(String value) {
        return SPLIT_PATTERN.matcher(normalize(value)).replaceAll("");
    }

    private BigDecimal nullToOne(BigDecimal value) {
        return value == null ? BigDecimal.ONE : value;
    }

    private Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private Integer nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 构造空召回结果。
     *
     * @param degraded true 表示召回过程异常或不可用；false 表示召回正常但没有命中证据
     */
    private L2RecallResult emptyResult(boolean degraded) {
        return L2RecallResult.builder()
                .esHits(List.of())
                .milvusHits(List.of())
                .degraded(degraded)
                .build();
    }

    /**
     * 内部临时评分对象，避免在排序阶段反复修改 L2FeatureHit。
     */
    private record ScoredFeature(RiskAttackFeatureDO feature, BigDecimal score) {
    }
}
