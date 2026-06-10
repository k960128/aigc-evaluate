package com.kant.llm.eval.search;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * L2 攻击特征 ES 召回命中。
 *
 * <p>该对象是 ES 原始命中的轻量包装，后续会被 RealEsPgL2RecallClient 转成统一的 L2FeatureHit。</p>
 */
@Data
@Builder
public class RiskFeatureEsHit {

    /** 命中的攻击特征 ID。 */
    private Long featureId;

    /** 命中特征所属风险小类 ID。 */
    private Long riskDetailsId;

    /** 命中特征所属风险大类 ID。 */
    private Long categoryId;

    /** 命中的特征文本，用于流水线日志展示。 */
    private String featureText;

    /** 特征类型，例如关键词、模式、输出模式。 */
    private String featureType;

    /** 特征极性：风险命中或安全例外。 */
    private String polarity;

    /** 特征风险等级。 */
    private Integer riskLevel;

    /** 特征权重。 */
    private BigDecimal weight;

    /** 内容哈希，便于从日志反查 ES 文档和 MySQL 源记录。 */
    private String contentHash;

    /** ES BM25 原始分，只在同一次查询内可比较。 */
    private Double rawScore;

    /** 按本次查询 maxScore 归一化后的 0-1 分数，供 L2 融合使用。 */
    private BigDecimal normalizedScore;
}
