package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新模型厂商请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateModelManufacturerRequest {
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
}
