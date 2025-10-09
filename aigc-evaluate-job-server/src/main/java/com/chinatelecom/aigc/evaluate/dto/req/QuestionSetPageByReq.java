package com.chinatelecom.aigc.evaluate.dto.req;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "管理后台 - 题目集分页By Request Req")
public class QuestionSetPageByReq extends PageParam {

    @ApiModelProperty("题目集ID")
    @NotBlank(message = "题目集ID不能为空")
    private String questionSetId;
    @ApiModelProperty("所属题库，前端做下拉框,精确匹配")
    private String category;
    @ApiModelProperty("题目难度，下拉框，精确匹配")
    private String difficulty;
    @ApiModelProperty("题目标题，模糊查询")
    private String title;
    @ApiModelProperty("数据来源，模糊查询")
    private String dataSource;
}
