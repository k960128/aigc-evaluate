package com.chinatelecom.aigc.evaluate.dto.resp;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 报告分页 Resp VO")
public class ReportRespPageResp extends PageParam  {

    @ApiModelProperty(value = "ID", example = "1234567890")
    private Long id;

    @ApiModelProperty(value = "用户ID", example = "1001")
    private Long userId;

    @ApiModelProperty(value = "任务ID", example = "2002")
    private Long taskId;

    @ApiModelProperty(value = "模型ID", example = "3003")
    private Long modelId;

    @ApiModelProperty(value = "文件名称", example = "任务报告")
    private String fileName;

    @ApiModelProperty(value = "文件路径", example = "/path/to/report.pdf")
    private String filePath;

    @ApiModelProperty(value = "阅读状态", example = "true")
    private Boolean readingStatus;

    @ApiModelProperty(value = "描述", example = "这是一个报告描述")
    private String desc;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;
}
