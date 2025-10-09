package com.chinatelecom.aigc.evaluate.dto.resp;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotEmpty;


@Data
@Builder
@ApiModel(value = "管理后台 - pdf生成请求 ReportResp Resp")
public class ReportResp {

    @ApiModelProperty(value = "报告主键", example = "1")
    @NotEmpty(message = "报告主键")
    private Long id;

    @ApiModelProperty(value = "任务 ID，精确匹配", example = "1")
    @NotEmpty(message = "任务 ID 不能为空")
    private String taskId;

    @ApiModelProperty(value = "模型 ID，精确匹配", example = "2")
    @NotEmpty(message = "任务 ID 不能为空")
    private String modelId;

    @ApiModelProperty(value = "用户 ID", example = "123")
    private String userId;

    @ApiModelProperty(value = "报告名字", example = "报告.pdf")
    private String name;

    @ApiModelProperty(value = "文件路径", example = "报告.pdf")
    private String filePath;

    @ApiModelProperty(value = "描述", example = "开始生产报告")
    private String desc;
}
