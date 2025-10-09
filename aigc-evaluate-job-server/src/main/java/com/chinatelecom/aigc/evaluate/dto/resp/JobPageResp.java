package com.chinatelecom.aigc.evaluate.dto.resp;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 定时任务分页 Resp VO")
public class JobPageResp extends PageParam {

    @ApiModelProperty(value = "任务名称，模糊匹配")
    private String name;

    @ApiModelProperty(value = "任务状态")
    private Integer status;

    @ApiModelProperty(value = "处理器的名字，模糊匹配")
    private String handlerName;

    @ApiModelProperty(value = "开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
