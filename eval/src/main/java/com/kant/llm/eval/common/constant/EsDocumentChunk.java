package com.kant.llm.eval.common.constant;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class EsDocumentChunk {

    private String id;

    private Long featureId;

    private Long riskDetailsId;

    private Long categoryId;

    private String featureCode;

    private String featureText;

    private String normalizedText;

    private String featureType;

    private String polarity;

    private Integer riskLevel;

    private String language;

    private String tags;

    private String source;

    private BigDecimal weight;

    private String contentHash;

    private Integer version;

    private Integer status;

    private String createTime;

    private String updateTime;

    private String content;

    private Map<String, Object> metadata;
}
