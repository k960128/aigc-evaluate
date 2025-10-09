package com.chinatelecom.aigc.evaluate.dto.req;

import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@ApiModel(value = "管理后台 - 任务修改 Request Req")
public class JobUpdateReq {
    /**
     * 主键
     */
    @ApiModelProperty(value = "任务编号", required = true, example = "1")
    @NotNull(message = "任务名称不能为空")
    private Long id;

    @ApiModelProperty(value = "任务名称", required = true, example = "测试任务名称")
    private String name;

    @ApiModelProperty(value = "任务参数", required = true, example = "{\"modelId\":6,\"questionSet\":[1,2,3]}")
    private String handlerParam;

    @ApiModelProperty(value = "任务描述", example = "这是一个临时任务")
    private String description;

    @ApiModelProperty(value = "CRON 表达式", example = "* 0/10 * * * ? *")
    private String cronExpression;

    @ApiModelProperty(value = "一次性执行", example = "2025-03-18 15:30:00")
    private String oneTimeExpression;

    @ApiModelProperty(value = "最大线程数量", example = "1")
    private Integer maxThreadSize;

    @ApiModelProperty(value = "调度类型 0:手动执行任务 1:一次性任务  2:周期性任", example = "1")
    private Integer runType;
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
