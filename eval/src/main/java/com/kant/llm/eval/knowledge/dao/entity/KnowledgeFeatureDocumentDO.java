package com.kant.llm.eval.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * L2 攻击特征知识 DO。
 * 新版本
 * <p>一条记录代表一条可召回的风险证据或安全例外证据。MySQL 是事实源，
 * ES/Milvus 只保存该表同步后的检索索引。</p>
 */
@Data
@TableName("knowledge_feature_document")
public class KnowledgeFeatureDocumentDO {

    /** 主键 ID，同时作为 ES 文档 ID 和 Milvus feature_id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 risk_details.id，L2 聚合和后续 L3 回查都以该字段为核心粒度。 */
    private Long riskDetailsId;

    /** 冗余归属小类名称 **/
    private String riskDetailsName;

    /** 冗余风险大类 ID，便于检索过滤和统计。 */
    private Long categoryId;

    /** 攻击特征原文、payload、诱导话术或安全例外样本。 */
    private String featureText;

    /** 归一化文本，用于检索和 content_hash 计算。 */
    private String normalizedText;

    /** 特征极性：UNSAFE-风险特征，SAFE_EXCEPTION-安全例外。 */
    private String polarity;

    /** 来源：manual、dataset、redteam、incident、generated。 */
    private String source;

    /** 特征权重，用于召回融合和阈值调整。 */
    private BigDecimal threshold;

    /** 归一化内容 hash，用于幂等去重。 */
    private String contentHash;

    /** 当前样本版本，来源knowledge_attack_feature_document_version_snapshot.version */
    private Integer currentVersion;

    /** 当前激活版本ID，指向 knowledge_attack_feature_document_version_snapshot.version_id */
    private Long currentVersionId;

    /** 综合同步状态：0-待同步，1-已同步，2-同步失败，3-已删除待同步。 */
    private Integer syncStatus;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;

    /** 创建人。 */
    private String creator;

    /** 更新人。 */
    private String updater;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0-未删除，1-已删除。 */
    @TableLogic
    private Boolean deleted;
}
