package com.kant.ai.infra.client;

import com.kant.ai.infra.enums.ModelManufacturerEnum;
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
public class ClientModelInfo {

    private Long modelId;
    private String model;
    private String apiKey;
    private String baseUrl;
    /**
     * 模型厂商标识
     */
    private ModelManufacturerEnum manufacturerType;

    /**
     * 用于测试连通性还是大模型CallBack
     * value:TEST_CONNECTION | CALL_BACK
     */
    @Builder.Default
    private String type = "CALL_BACK";
}
