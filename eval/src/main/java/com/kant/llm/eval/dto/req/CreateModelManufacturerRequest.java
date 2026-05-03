package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建模型厂商请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateModelManufacturerRequest {
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
