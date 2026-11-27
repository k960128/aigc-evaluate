package com.kant.llm.eval.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@TableName("knowledge_risk_word")
public class KnowledgeRiskWordDO {

    /** 主键 ID，同时作为 ES 文档 ID 和 Milvus feature_id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 risk_details.id，L2 聚合和后续 L3 回查都以该字段为核心粒度。 */
    private Long riskDetailsId;

    /** 字面量特征词 */
    @TableField("keyword")
    private String keyword;

    /** 匹配模式:   1-精确匹配,   2-正则匹配 */
    @TableField("match_type")
    private Integer matchType;

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
