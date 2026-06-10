package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * L2 攻击特征保存请求。
 *
 * <p>创建时 id 为空；更新时 id 必填。
 * 服务层会根据 riskDetailsId 自动补齐 categoryId，计算 contentHash，
 * 并把综合/ES/Milvus 同步状态置为待同步。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveL2RiskAttackFeatureRequest {

    /** 主键 ID，创建时为空，更新时必填。 */
    private Long id;

    /** 风险小类 ID，对应 risk_details.id；创建时必填，更新时不传则沿用原小类。 */
    private Long riskDetailsId;

    /** 风险大类 ID；未传时会根据 risk_details.category_id 自动补齐。 */
    private Long categoryId;

    /** 特征业务编码，用于人工维护和外部系统对账，不参与唯一性判断。 */
    private String featureCode;

    /** 攻击特征原文、payload、诱导话术或安全例外样本；创建时必填。 */
    private String featureText;

    /** 归一化文本；未传时默认使用 featureText，后续 contentHash 也基于该字段生成。 */
    private String normalizedText;

    /** 特征类型，如 KEYWORD、PROMPT_PATTERN、RESPONSE_PATTERN、SAFE_EXCEPTION 等。 */
    private String featureType;

    /** 特征极性：UNSAFE 表示风险证据，SAFE_EXCEPTION 表示安全例外/降误报证据。 */
    private String polarity;

    /** 风险等级：1-低，2-中，3-高，4-致命；Mock 召回排序会参考该字段。 */
    private Integer riskLevel;

    /** 语言：zh-CN、en-US、mixed 等；未传时默认 zh-CN。 */
    private String language;

    /** 标签 JSON 字符串，用于记录场景、来源、策略批次等扩展信息。 */
    private String tags;

    /** 来源：manual、dataset、redteam、incident、generated 等；未传时默认 manual。 */
    private String source;

    /** 特征权重；Mock 召回会用它放大或收敛命中分，未传时默认 1。 */
    private BigDecimal weight;

    /** 业务状态：0-禁用，1-启用；未传时创建默认启用。 */
    private Integer status;

    /** 创建人；仅在创建或显式传入时写入。 */
    private String creator;

    /** 更新人；用于后台审计和问题追踪。 */
    private String updater;
}
