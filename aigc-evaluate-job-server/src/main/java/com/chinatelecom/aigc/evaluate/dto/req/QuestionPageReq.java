package com.chinatelecom.aigc.evaluate.dto.req;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "管理后台 - 题目分页 Request Req")
public class QuestionPageReq extends PageParam {

    @ApiModelProperty("题目标题，模糊匹配")
    private String title;

    @ApiModelProperty("题目难度，模糊匹配")
    private String difficulty;

    @ApiModelProperty("所属题库，模糊匹配")
    private String category;

    @ApiModelProperty("攻击方式，模糊匹配")
    private String attackMethod;

    @ApiModelProperty("数据来源，模糊查询")
    private String dataSource;

    @ApiModelProperty("标签，模糊匹配")
    private String tags;
}
