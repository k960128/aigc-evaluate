package com.chinatelecom.aigc.evaluate.dto.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
@Builder
@ApiModel(value = "管理后台 - 定时任务创建/修改 Request Req")
public class JobSaveReq {
    @ApiModelProperty(value = "任务名称", required = true, example = "测试任务名称")
    @NotBlank(message = "任务名称不能为空")
    private String name;

    @ApiModelProperty(value = "任务参数", required = true, example = "{\"modelId\":[6,7],\"questionSet\":[1,2,3]}")
    @NotBlank(message = "任务参数不能为空")
    private String handlerParam;

    @ApiModelProperty(value = "任务描述", example = "这是一个临时任务")
    private String description;

    @ApiModelProperty(value = "CRON 表达式", example = "* 0/10 * * * ? *")
    private String cronExpression;

    @ApiModelProperty(value = "一次性执行", example = "2025-03-18 15:30:00")
    private String oneTimeExpression;

    @ApiModelProperty(value = "调度类型 0:手动执行任务 1:一次性任务  2:周期性任", example = "1")
    private Integer runType;

    @ApiModelProperty(value = "最大线程数量", example = "1")
    private Integer maxThreadSize;
    /*
    @ApiModelProperty("元模型类别代码")
    private Long id;

    @ApiModelProperty(value = "处理器的名称", required = true, example = "处理器的名称")
    private String handlerName;

    @ApiModelProperty(value = "重试次数", required = true, example = "1")
    private Integer retryCount;

    @ApiModelProperty(value = "重试间隔", required = true, example = "1000")
    private Integer retryInterval;

    @ApiModelProperty(value = "监控超时时间", example = "1000")
    private Integer monitorTimeout;
     */
}

