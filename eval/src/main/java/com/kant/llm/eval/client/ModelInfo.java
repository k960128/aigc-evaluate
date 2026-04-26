package com.kant.llm.eval.client;

import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {

    private Long modelId;
    private String model;
    private String apiKey;
    private String baseUrl;
    /**
     * 模型厂商标识
     */
    private ModelManufacturerEnum manufacturerType;
}
