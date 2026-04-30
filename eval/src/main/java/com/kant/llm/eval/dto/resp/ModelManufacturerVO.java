package com.kant.llm.eval.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelManufacturerVO {
    private Integer id;
    private String manufacturerName;
    private String manufacturerCode;
    private String defaultBaseUrl;
    private String describe;
    private String icon;
    private Boolean enable;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}