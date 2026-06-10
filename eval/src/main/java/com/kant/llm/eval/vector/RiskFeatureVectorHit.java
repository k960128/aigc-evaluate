package com.kant.llm.eval.vector;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Risk feature vector search hit.
 */
@Data
@Builder
public class RiskFeatureVectorHit {

    private Long featureId;

    private Long riskDetailsId;

    private Long categoryId;

    private String featureText;

    private String featureType;

    private String polarity;

    private Integer riskLevel;

    private BigDecimal weight;

    private Double score;

    private Map<String, Object> metadata;
}
