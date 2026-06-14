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
public class KbSyncEventVO {

    private Long id;

    private String eventId;

    private String aggregateType;

    private Long aggregateId;

    private String operationType;

    private Long riskDetailsId;

    private String contentHash;

    private Integer version;

    private String payload;

    private Integer esStatus;

    private Integer milvusStatus;

    private Integer pgStatus;

    private Integer retryCount;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime nextRetryTime;

    private String lastError;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
