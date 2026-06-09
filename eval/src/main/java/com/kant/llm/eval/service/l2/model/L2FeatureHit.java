package com.kant.llm.eval.service.l2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * L2 单条攻击特征命中结果。
 *
 * <p>同一 featureId 可能同时来自 ES 和 Milvus，RRF 阶段会合并来源、rank 和原始分数。</p>
 */
@Data
@Builder
public class L2FeatureHit {

    /** 攻击特征 ID，对应 risk_attack_feature.id。 */
    private Long featureId;

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 风险小类名称，用于日志可读性。 */
    private String detailsName;

    /** 风险大类 ID。 */
    private Long categoryId;

    /** 风险大类名称。 */
    private String categoryName;

    /** 特征文本摘要或原文。 */
    private String featureText;

    /** 特征类型。 */
    private String featureType;

    /** 特征极性：UNSAFE 或 SAFE_EXCEPTION。 */
    private String polarity;

    /** 风险等级：1-低，2-中，3-高，4-致命。 */
    private Integer riskLevel;

    /** 严重等级：通常来自 risk_detail_rule。 */
    private Integer severityLevel;

    /** 特征权重。 */
    private BigDecimal weight;

    /** 命中来源：ES、MILVUS。 */
    private List<String> sources;

    /** ES 召回排名。 */
    private Integer esRank;

    /** ES 原始分或归一化分。 */
    private BigDecimal esScore;

    /** Milvus 召回排名。 */
    private Integer milvusRank;

    /** Milvus 相似度。 */
    private BigDecimal milvusSimilarity;

    /** RRF 融合分。 */
    private BigDecimal rrfScore;

    /** Reranker 精排分。 */
    private BigDecimal rerankScore;
}
