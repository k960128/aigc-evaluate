package com.chinatelecom.aigc.evaluate.dto.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 批量删除 Response VO")
public class QuestionBatchResp {
    @ApiModelProperty(value = "删除题目数量")
    private String deleted_count = "0";
    @ApiModelProperty(value = "删除题目失败数量")
    private String failed_count = "0";
}
