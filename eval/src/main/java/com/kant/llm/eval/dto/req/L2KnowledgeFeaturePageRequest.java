package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 攻击特征分页查询请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2KnowledgeFeaturePageRequest {

    /** 当前页码，默认 1。 */
    private Integer current;

    /** 每页数量，默认 10。 */
    private Integer size;

    /** 风险大类 ID，对应 risk_category.id。 */
    private Long categoryId;

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 特征类型：KEYWORD、PAYLOAD、PROMPT_PATTERN、RESPONSE_PATTERN、JAILBREAK、SIMILAR_CASE、SAFE_EXCEPTION。 */
    private String featureType;

    /** 特征极性：UNSAFE-风险特征，SAFE_EXCEPTION-安全例外。 */
    private String polarity;

    /** 风险等级：1-低，2-中，3-高，4-致命。 */
    private Integer riskLevel;

    /** 综合同步状态：0-待同步，1-已同步，2-同步失败，3-已删除待同步。 */
    private Integer syncStatus;

    /** ES 同步状态：0-待同步，1-已同步，2-同步失败。 */
    private Integer esSyncStatus;

    /** PGVector 同步状态：0-待同步，1-已同步，2-同步失败。 */
    private Integer pgSyncStatus;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;

    /** 特征文本关键词，支持模糊查询 feature_text 和 normalized_text。 */
    private String keyword;
}
