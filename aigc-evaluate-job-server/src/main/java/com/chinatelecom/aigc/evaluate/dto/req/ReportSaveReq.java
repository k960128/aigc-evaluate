package com.chinatelecom.aigc.evaluate.dto.req;


import com.baomidou.mybatisplus.annotation.TableField;
import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "管理后台 - pdf生成请求 Request Req")
public class ReportSaveReq extends PageParam  {
    @ApiModelProperty(value = "ID", example = "0")
    private Long id;

    @ApiModelProperty(value = "任务 ID，精确匹配", example = "1")
    private String taskId;

    @ApiModelProperty(value = "模型 ID，精确匹配", example = "2")
    private String modelId;

    @ApiModelProperty(value = "用户 ID，精确匹配", example = "3")
    private String userId;

    @ApiModelProperty(value = "报告类型", example = "pdf或者excel")
    private String reportType; // 取值 pdf 或 excel

    @ApiModelProperty(value = "报告名字", example = "报告")
    private String name;

    @ApiModelProperty(value = "描述", example = "")
    private String desc;

    @ApiModelProperty(value = "人工描述", example = "")
    private String manualDesc;

    @ApiModelProperty(value = "阅读状态", example = "true")
    private Boolean readingStatus;
}
