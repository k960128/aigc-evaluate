package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * L2 攻击特征更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateL2KnowledgeFeatureRequest {

    /** 特征 ID。 */
    private Long id;

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 风险大类 ID，对应 risk_category.id。 */
    private Long categoryId;

    /** 特征业务编码。 */
    private String featureCode;

    /** 攻击特征原文、违规回答片段或安全例外样本。 */
    private String featureText;

    /** 归一化文本。 */
    private String normalizedText;

    /** 特征类型。 */
    private String featureType;

    /** 特征极性：UNSAFE 或 SAFE_EXCEPTION。 */
    private String polarity;

    /** 风险等级：1-低，2-中，3-高，4-致命。 */
    private Integer riskLevel;

    /** 语言：zh-CN、en-US、mixed 等。 */
    private String language;

    /** 标签 JSON 字符串。 */
    private String tags;

    /** 来源：manual、dataset、redteam、incident、generated。 */
    private String source;

    /** 特征权重。 */
    private BigDecimal weight;

    /** 内容 hash。 */
    private String contentHash;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;

    /** 更新人。 */
    private String updater;
}
