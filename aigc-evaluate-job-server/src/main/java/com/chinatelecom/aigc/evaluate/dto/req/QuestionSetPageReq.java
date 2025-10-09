package com.chinatelecom.aigc.evaluate.dto.req;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "管理后台 - 题目集分页 Request Req")
public class QuestionSetPageReq extends PageParam {

    @ApiModelProperty("题目集名称，模糊匹配")
    private String questionSetName;

    @ApiModelProperty("所属题库，模糊匹配")
    private String questionCategory;
}
