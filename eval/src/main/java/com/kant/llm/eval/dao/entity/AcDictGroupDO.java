package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AC极速字典分组表
 */
@Data
@TableName("ac_dict_group")
public class AcDictGroupDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典分组名称 (如: 涉黄与黑产引流) */
    private String name;

    /** 分组描述 */
    private String description;


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