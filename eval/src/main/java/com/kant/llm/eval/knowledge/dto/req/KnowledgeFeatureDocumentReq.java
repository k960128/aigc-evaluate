package com.kant.llm.eval.knowledge.dto.req;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFeatureDocumentReq {
    /** 关联 risk_details.id，L2 聚合和后续 L3 回查都以该字段为核心粒度。 */
    private Long riskDetailsId;

    /** 冗余风险大类 ID，便于检索过滤和统计。 */
    private Long categoryId;

    /** 攻击特征原文、payload、诱导话术或安全例外样本。 */
    private String featureText;

    /** 特征极性：UNSAFE-风险特征，SAFE_EXCEPTION-安全例外。 */
    private String polarity;

    /** 样本来源：互联网、AI合成、人工标注等 */
    private String source;

    /** 特征权重，用于召回融合和阈值调整。 */
    private BigDecimal threshold;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;
}
