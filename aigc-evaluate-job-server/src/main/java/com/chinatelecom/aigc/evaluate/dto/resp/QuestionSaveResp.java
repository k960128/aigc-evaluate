package com.chinatelecom.aigc.evaluate.dto.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 添加题目返回 Resp VO")
public class QuestionSaveResp {

    @ApiModelProperty(value = "题目唯一标识ID")
    private String questionId;
}
