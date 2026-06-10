package com.kant.llm.eval.search;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * L2 攻击特征 ES 文档。
 *
 * <p>字段基本来自 risk_attack_feature，ES 只保存检索和召回展示所需字段。
 * MySQL 仍是唯一事实源，ES 文档可以通过 kb_sync_event 重建。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFeatureEsDocument {

    /** 攻击特征主键，对应 risk_attack_feature.id，同时作为 ES document id。 */
    private Long featureId;

    /** 风险小类 ID，是 L2 聚合 riskDetailHits 的统一粒度。 */
    private Long riskDetailsId;

    /** 风险大类 ID，用于召回后补齐分类信息和日志展示。 */
    private Long categoryId;

    /** 稳定特征编码，例如 MOCK-D8-01，便于人工定位规则来源。 */
    private String featureCode;

    /** 原始特征文本，ES 中使用 IK 分词并给予较高权重。 */
    private String featureText;

    /** 归一化特征文本，用于和原文一起提升关键词召回稳定性。 */
    private String normalizedText;

    /** 特征类型，例如 KEYWORD、PATTERN、OUTPUT_PATTERN。 */
    private String featureType;

    /** 特征极性，UNSAFE 表示风险命中，SAFE_EXCEPTION 表示安全例外。 */
    private String polarity;

    /** 特征风险等级，召回后参与排序和日志展示。 */
    private Integer riskLevel;

    /** 特征语言，当前 Mock 主要为 zh-CN，后续可支持多语言特征。 */
    private String language;

    /** 标签文本，用于 ES 低权重辅助召回。 */
    private String tags;

    /** 特征来源，例如 mock_l2_seed 或人工维护来源。 */
    private String source;

    /** 特征权重，后续可用于融合时对不同特征做加权。 */
    private BigDecimal weight;

    /** 内容哈希，用于幂等同步和人工排查同内容重复写入。 */
    private String contentHash;

    /** 特征版本号，知识库更新时用于判断索引是否同步到最新版本。 */
    private Integer version;

    /** 状态：1 启用，0 禁用；ES 查询只过滤启用特征。 */
    private Integer status;

    /** 创建时间字符串，主要用于 ES 文档展示和排查。 */
    private String createTime;

    /** 更新时间字符串，主要用于 ES 文档展示和排查。 */
    private String updateTime;
}
