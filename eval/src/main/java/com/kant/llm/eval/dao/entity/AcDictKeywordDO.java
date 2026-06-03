package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AC极速字典特征词表
 */
@Data
@TableName("ac_dict_keyword")
public class AcDictKeywordDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属分组ID (关联 ac_dict_group.id) */
    private Long groupId;

    /** 字面量特征词 */
    private String keyword;

    /** 风险等级: 1-致命级别(命中即直接拦截), 2-疑似级别(仅打标签透传) */
    private Integer riskLevel;

    /** 匹配模式: 1-精确匹配, 2-模糊包含匹配 */
    private Integer matchType;

    /** Redis同步状态: 0-待同步, 1-已同步 */
    private Integer syncStatus;

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

    /** 是否删除 0未删除 1删除 */
    @TableLogic
    private Boolean deleted;
}