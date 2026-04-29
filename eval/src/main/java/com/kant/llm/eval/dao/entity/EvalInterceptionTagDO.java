package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 拦截标签表 DO
 *
 * @author 后端源码
 */
@TableName("eval_interception_tag")
@KeySequence("eval_interception_tag_seq")
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalInterceptionTagDO {

    /** ID */
    @TableId
    private Long id;

    /** 标签编码 */
    private String tagCode;

    /** 标签中文名称 */
    private String tagName;

    /** 标签中文描述 */
    private String tagDesc;

    /** 层级 */
    private Integer level;

    /** 父级标签ID */
    private Long parentId;

    /** 是否启用 0-禁用 1-启用 */
    private Boolean enabled;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 是否删除 0未删除 1删除 */
    @TableLogic
    private Boolean deleted;

}