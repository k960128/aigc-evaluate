package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型连通性测试请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConnectivityTestRequest {
    /** 模型名称 */
    private String model;
    /** 模型URL */
    private String baseUrl;
    /** 应用密钥 */
    private String apiKey;
    /** 厂商编码 */
    private String manufacturerCode;
}
