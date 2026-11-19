package com.kant.llm.eval.fingerprint.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 负向指纹表 (黑名单：高危红线违禁词)
 */
@Data
@TableName("knowledge_negative_fingerprint")
public class KnowledgeNegativeFingerprintDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联31小类标签 */
    private Long riskDetailsId;

    /** 字面量特征词 */
    private String keyword;

    /** 风险等级: 1-致命级别(命中即直接拦截), 2-疑似级别(仅打标签透传) */
    private Integer riskLevel;

    /** 匹配模式: 1-精确匹配, 2-正则匹配 */
    private Integer matchType;

    /** Redis同步状态: 0-待同步, 1-已同步 */
    private Boolean syncStatus;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标识(0-未删除, 1-已删除) */
    @TableLogic
    private Boolean deleted;
}
