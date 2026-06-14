package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAttackFeatureVO {

    private Long id;

    private Long riskDetailsId;

    private String riskDetailsName;

    private Long categoryId;

    private String featureCode;

    private String featureText;

    private String normalizedText;

    private String featureType;

    private String polarity;

    private Integer riskLevel;

    private String language;

    private String tags;

    private String source;

    private BigDecimal weight;

    private String contentHash;

    private Integer version;

    private Integer syncStatus;

    private Integer esSyncStatus;

    private Integer milvusSyncStatus;

    private Integer pgSyncStatus;

    private Integer status;

    private String creator;

    private String updater;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;

    private Boolean deleted;
}
