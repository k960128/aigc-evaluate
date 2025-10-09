package com.chinatelecom.aigc.evaluate.dto.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@ApiModel(value = "管理后台 - 定时任务日志 Response VO")
@Data
public class JobLogResp {

    @ApiModelProperty(value = "日志ID")
    private Long id;

    @ApiModelProperty(value = "任务ID")
    private Long jobId;

    @ApiModelProperty(value = "处理器的名字")
    private String handlerName;

    @ApiModelProperty(value = "处理器的参数")
    private String handlerParam;

    @ApiModelProperty(value = "第几次执行")
    private Integer executeIndex;

    @ApiModelProperty(value = "开始执行时间")
    private LocalDateTime beginTime;

    @ApiModelProperty(value = "结束执行时间")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "执行时长")
    private Integer duration;

    @ApiModelProperty(value = "任务状态")
    private Integer status;

    @ApiModelProperty(value = "结果数据")
    private String result;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

}
