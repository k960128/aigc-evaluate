package com.kant.llm.eval.service.l2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * L2 阈值配置快照。
 *
 * <p>第一阶段先使用代码默认值，后续可以替换为配置中心或数据库版本化阈值。</p>
 */
@Data
@Builder
public class L2ThresholdProperties {

    /** ES 默认召回数量。 */
    private Integer esTopK;

    /** Milvus 默认召回数量。 */
    private Integer milvusTopK;

    /** RRF 融合公式中的平滑参数 k。 */
    private Integer rrfK;

    /** RRF 融合后进入 Reranker 的候选数量。 */
    private Integer rrfTopN;

    /** Reranker 高置信阈值。 */
    private BigDecimal rerankHighThreshold;

    /** Reranker 低风险阈值。 */
    private BigDecimal rerankLowThreshold;

    /** 风险小类聚合后的违规短路阈值。 */
    private BigDecimal detailUnsafeThreshold;

    /** 风险小类聚合后的安全短路阈值。 */
    private BigDecimal detailSafeThreshold;

    /** Milvus 高相似阈值。 */
    private BigDecimal milvusHighSimilarity;

    /**
     * 创建第一版默认阈值。
     */
    public static L2ThresholdProperties defaults() {
        return L2ThresholdProperties.builder()
                .esTopK(30)
                .milvusTopK(30)
                .rrfK(60)
                .rrfTopN(20)
                .rerankHighThreshold(new BigDecimal("0.85"))
                .rerankLowThreshold(new BigDecimal("0.45"))
                .detailUnsafeThreshold(new BigDecimal("0.85"))
                .detailSafeThreshold(new BigDecimal("0.35"))
                .milvusHighSimilarity(new BigDecimal("0.82"))
                .build();
    }
}
