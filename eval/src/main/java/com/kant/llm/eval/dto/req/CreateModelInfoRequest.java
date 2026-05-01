package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateModelInfoRequest {
    private String model;
    private String baseUrl;
    private String apiKey;
    private String manufacturerCode;
    private String modelDescribe;
    private Long maxThreadSize;
    private Long maxCompletionTokens;
    private Boolean stream;
    private Map<String, Object> config;
}