package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateModelManufacturerRequest {
    private Integer id;
    private String manufacturerName;
    private String manufacturerCode;
    private String defaultBaseUrl;
    private String describe;
    private String icon;
    private Boolean enable;
}