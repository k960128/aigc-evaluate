package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型厂商视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelManufacturerVO {
    /** 厂商ID */
    private Integer id;
    /** 厂商名称 */
    private String manufacturerName;
    /** 厂商编码 */
    private String manufacturerCode;
    /** 默认基础URL */
    private String defaultBaseUrl;
    /** 描述 */
    private String describe;
    /** 图标 */
    private String icon;
    /** 是否启用 */
    private Boolean enable;
    /** 创建时间 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;
    /** 更新时间 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
