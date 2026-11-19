package com.kant.llm.common.constant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EsDocumentChunkV2 {

    private String id;

    private String documentId;

    private Long riskDetailsId;

    private Long categoryId;

    private String featureText;

    private String normalizedText;

    private String polarity;

    private String source;

    private BigDecimal threshold;

    private String contentHash;

    private Integer status;

    private String createTime;

    private String updateTime;

    /**
     * ES 原始 BM25 分数。
     *
     * <p>该字段不写入 ES 文档，只在召回结果返回时由 ElasticSearchService 回填。</p>
     */
    private Double score;

    /**
     * ES 分数按本次查询 maxScore 归一化后的 0-1 分数。
     *
     * <p>L2 真实召回会将该字段映射到 L2FeatureHit.esScore。</p>
     */
    private BigDecimal normalizedScore;
}
