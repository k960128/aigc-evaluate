package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConnectivityTestRequest {
    private String model;
    private String baseUrl;
    private String apiKey;
    private String manufacturerCode;
}