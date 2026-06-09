package com.kant.llm.eval.service.l2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * L2 按风险小类聚合后的命中结果。
 */
@Data
@Builder
public class L2RiskDetailHit {

    /** 风险小类 ID。 */
    private Long riskDetailsId;

    /** 风险小类名称。 */
    private String detailsName;

    /** 风险大类 ID。 */
    private Long categoryId;

    /** 风险大类名称。 */
    private String categoryName;

    /**
     * 小类聚合分。
     *
     * <p>该分数不是单个模型输出，而是由最高 rerank 分、UNSAFE 命中数、强 ES 命中、
     * 高 Milvus 相似、最高风险等级和 SAFE_EXCEPTION 扣分共同计算得到。</p>
     */
    private BigDecimal detailScore;

    /** 该小类下最高风险等级。 */
    private Integer maxRiskLevel;

    /** 该小类下风险特征命中数量。 */
    private Integer unsafeHitCount;

    /** 该小类下安全例外命中数量。 */
    private Integer safeExceptionHitCount;

    /** 该小类下 ES 强命中数量。 */
    private Integer strongEsHitCount;

    /** 该小类下 Milvus 高相似命中数量。 */
    private Integer highMilvusHitCount;

    /** 聚合小类下的特征命中明细。 */
    private List<L2FeatureHit> featureHits;
}
