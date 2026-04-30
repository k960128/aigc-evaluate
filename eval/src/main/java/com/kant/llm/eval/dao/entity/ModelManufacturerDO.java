package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 模型厂商表 DO
 *
 * @author 后端源码
 */
@TableName("model_manufacturer")
@KeySequence("model_manufacturer_seq")
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelManufacturerDO {

    /** ID */
    @TableId
    private Integer id;

    /** 厂商名称 */
    private String manufacturerName;

    /** 厂商编码 */
    private String manufacturerCode;

    /** 默认BaseURL */
    private String defaultBaseUrl;

    /** 厂商信息描述 */
    private String describe;

    /** 图标 */
    private String icon;

    /** 是否启用 0-禁用 1-启用 */
    private Boolean enable;

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