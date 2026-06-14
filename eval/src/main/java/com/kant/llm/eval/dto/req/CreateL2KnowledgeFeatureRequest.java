package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * L2 攻击特征创建请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateL2KnowledgeFeatureRequest {

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 风险大类 ID，对应 risk_category.id。 */
    private Long categoryId;

    /** 攻击特征原文、违规回答片段或安全例外样本。 */
    private String featureText;

    /** 特征极性：UNSAFE 或 SAFE_EXCEPTION。 */
    private String polarity;
}
