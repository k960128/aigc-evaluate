package com.chinatelecom.aigc.evaluate.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportBaseInfoResp {
    @ApiModelProperty("评测目标")
    private String target; // modelName + modelVersion

    @ApiModelProperty("评测任务名称")
    private String taskName;

    @ApiModelProperty("评测开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty("评测结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
