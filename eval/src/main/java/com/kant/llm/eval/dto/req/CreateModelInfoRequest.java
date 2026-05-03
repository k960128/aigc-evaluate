package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 创建模型信息请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateModelInfoRequest {
    /** 模型名称 */
    private String model;
    /** 模型URL */
    private String baseUrl;
    /** 应用密钥 */
    private String apiKey;
    /** 厂商编码 */
    private String manufacturerCode;
    /** 模型描述 */
    private String modelDescribe;
    /** 最大并发数 */
    private Long maxThreadSize;
    /** 生成词元数量 */
    private Long maxCompletionTokens;
    /** 流式响应 */
    private Boolean stream;
    /** 扩展配置 */
    private Map<String, Object> config;
}
