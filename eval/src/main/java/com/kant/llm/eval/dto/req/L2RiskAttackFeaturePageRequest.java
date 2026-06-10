package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 攻击特征分页查询请求。
 *
 * <p>攻击特征是 L2 召回的最小知识单元。
 * 这些筛选条件主要服务于后台维护、问题排查和索引同步状态观测。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2RiskAttackFeaturePageRequest {

    /** 当前页码，默认 1；传空或小于等于 0 时由服务层兜底。 */
    private Integer current;

    /** 每页数量，默认 10；传空或小于等于 0 时由服务层兜底。 */
    private Integer size;

    /** 风险大类 ID，对应 risk_category.id，可用于按一级风险域筛选。 */
    private Long categoryId;

    /** 风险小类 ID，对应 risk_details.id，是 L2 聚合命中的核心粒度。 */
    private Long riskDetailsId;

    /** 特征文本关键词，支持按 feature_text 做模糊查询。 */
    private String keyword;

    /** 特征类型：KEYWORD、PAYLOAD、PROMPT_PATTERN、RESPONSE_PATTERN、JAILBREAK、SIMILAR_CASE、SAFE_EXCEPTION。 */
    private String featureType;

    /** 特征极性：UNSAFE-违规风险证据，SAFE_EXCEPTION-安全例外/降误报证据。 */
    private String polarity;

    /** 综合同步状态：0-待同步，1-已同步，2-同步失败，3-已删除待同步。 */
    private Integer syncStatus;

    /** ES 同步状态：0-待同步，1-已同步，2-同步失败。 */
    private Integer esSyncStatus;

    /** Milvus 同步状态：0-待同步，1-已同步，2-同步失败。 */
    private Integer milvusSyncStatus;

    /** 业务状态：0-禁用，1-启用；禁用后不应参与 L2 召回。 */
    private Integer status;
}
