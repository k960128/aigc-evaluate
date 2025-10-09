package com.chinatelecom.aigc.evaluate.dto.resp;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static com.chinatelecom.aigc.evaluate.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 定时任务日志分页 Resp")
public class JobLogPageResp extends PageParam {

    @ApiModelProperty(value = "任务ID")
    private Long jobId;

    @ApiModelProperty(value = "处理器的名字，模糊匹配")
    private String handlerName;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @ApiModelProperty(value = "开始执行时间")
    private LocalDateTime beginTime;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @ApiModelProperty(value = "结束执行时间")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "任务状态，参见 JobLogStatusEnum 枚举")
    private Integer status;

}
