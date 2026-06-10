package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * L2 攻击特征响应对象。
 *
 * <p>攻击特征是 L2 召回链路的可维护知识单元。
 * 响应中同时带出风险层级、同步状态和内容 hash，方便后台定位特征是否已经进入索引或 Mock 召回。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2RiskAttackFeatureVO {

    /** 主键 ID，对应 risk_attack_feature.id。 */
    private Long id;

    /** 风险小类 ID，对应 risk_details.id，也是 L2 聚合命中的核心粒度。 */
    private Long riskDetailsId;

    /** 风险小类名称，用于后台展示和 L2 日志可读性。 */
    private String detailsName;

    /** 风险大类 ID，对应 risk_category.id。 */
    private Long categoryId;

    /** 风险大类名称，用于后台展示。 */
    private String categoryName;

    /** 特征业务编码，用于人工维护和外部对账，不参与唯一性判断。 */
    private String featureCode;

    /** 特征原文，即召回使用的主要文本证据。 */
    private String featureText;

    /** 归一化文本；contentHash 和 Mock 召回优先基于该字段处理。 */
    private String normalizedText;

    /** 特征类型，如 KEYWORD、PROMPT_PATTERN、RESPONSE_PATTERN、SAFE_EXCEPTION 等。 */
    private String featureType;

    /** 特征极性：UNSAFE-违规风险证据，SAFE_EXCEPTION-安全例外/降误报证据。 */
    private String polarity;

    /** 风险等级：1-低，2-中，3-高，4-致命；Mock 召回排序会参考该字段。 */
    private Integer riskLevel;

    /** 语言，如 zh-CN、en-US、mixed。 */
    private String language;

    /** 标签 JSON 字符串，用于保存策略批次、场景、来源等扩展信息。 */
    private String tags;

    /** 来源，如 manual、dataset、redteam、incident、generated。 */
    private String source;

    /** 特征权重；Mock 召回会用它调整命中分。 */
    private BigDecimal weight;

    /** 内容 hash，由 riskDetailsId、polarity、normalizedText 生成，用于去重和索引幂等。 */
    private String contentHash;

    /** 特征版本；创建为 1，每次编辑、启停或删除前递增。 */
    private Integer version;

    /** 综合同步状态：0-待同步，1-已同步，2-同步失败，3-已删除待同步。 */
    private Integer syncStatus;

    /** 综合同步状态说明，来自 KbSyncStatusEnums。 */
    private String syncStatusDesc;

    /** ES 同步状态：0-待同步，1-已同步，2-同步失败。 */
    private Integer esSyncStatus;

    /** Milvus 同步状态：0-待同步，1-已同步，2-同步失败。 */
    private Integer milvusSyncStatus;

    /** 业务状态：0-禁用，1-启用；禁用特征不会参与 L2 召回。 */
    private Integer status;

    /** 创建人。 */
    private String creator;

    /** 更新人。 */
    private String updater;

    /** 创建时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
