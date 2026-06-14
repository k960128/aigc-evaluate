package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDetailRuleVO {

    private Long id;

    private Long riskDetailsId;

    private String judgeRule;

    private Integer severityLevel;

    private String decisionBoundary;

    private String unsafeExamples;

    private String safeExamples;

    private Integer version;

    private Integer status;

    private String creator;

    private String updater;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;

    private Boolean deleted;
}
