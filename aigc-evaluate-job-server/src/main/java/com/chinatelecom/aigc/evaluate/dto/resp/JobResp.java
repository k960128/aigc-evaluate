package com.chinatelecom.aigc.evaluate.dto.resp;

import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Data
@ApiModel(value = "管理后台 - 定时任务 Response VO")
public class JobResp {

    @ApiModelProperty(value = "任务ID")
    private Long id;

    @ApiModelProperty(value = "任务名称")
    private String name;

    @ApiModelProperty(value = "任务描述")
    private String description;

    @ApiModelProperty(value = "任务状态")
    private Integer status;

    @ApiModelProperty(value = "模型信息")
    private List<ModelInfoDO> modelInfoSet;

    @ApiModelProperty(value = "模型信息K-V")
    private Map<Long,ModelInfoDO> modelInfoMap;

    @ApiModelProperty(value = "习题集信息")
    private List<QuestionSetInfoDO> questionSet;

    @ApiModelProperty(value = "处理器的名字")
    private String handlerName;

    @ApiModelProperty(value = "处理器的参数")
    private String handlerParam;

    @ApiModelProperty(value = "CRON 表达式")
    private String cronExpression;

    @ApiModelProperty(value = "一次性执行")
    private String oneTimeExpression;

    @ApiModelProperty(value = "最大线程数量")
    private Integer maxThreadSize;

    @ApiModelProperty(value = "调度类型 0:手动执行任务 1:一次性任务  2:周期性")
    private Integer runType;

    @ApiModelProperty(value = "重试次数")
    private Integer retryCount;

    @ApiModelProperty(value = "重试间隔")
    private Integer retryInterval;

    @ApiModelProperty(value = "监控超时时间")
    private Integer monitorTimeout;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
