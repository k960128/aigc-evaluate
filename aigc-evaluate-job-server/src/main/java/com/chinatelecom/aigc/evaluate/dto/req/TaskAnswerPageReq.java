package com.chinatelecom.aigc.evaluate.dto.req;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 任务答案分页 Request Req")
public class TaskAnswerPageReq extends PageParam {

    @ApiModelProperty(value = "任务 ID，精确匹配", example = "1")
    private String taskId;

    @ApiModelProperty(value = "模型 ID，精确匹配", example = "2")
    private String modelId;

    @ApiModelProperty(value = "题目 ID，精确匹配", example = "67890")
    private String questionId;

    @ApiModelProperty(value = "任务场景，模糊匹配", example = "问答")
    private String taskScenario;

    @ApiModelProperty(value = "问题集合，模糊匹配", example = "测试数据集A")
    private String questionSet;

    @ApiModelProperty(value = "应用名称，模糊匹配", example = "MyApp")
    private String appName;

    @ApiModelProperty(value = "模型名称，模糊匹配", example = "GPT-4")
    private String modelName;

    @ApiModelProperty(value = "模型版本，模糊匹配", example = "1.0.0")
    private String modelVersion;

    @ApiModelProperty(value = "所属题库，精确匹配", example = "FORWARD | NEGATIVE")
    private String questionCategory;

    @ApiModelProperty(value = "问题内容，模糊匹配", example = "你是谁？")
    private String questionContent;

    @ApiModelProperty(value = "评判结果，精确匹配", example = "0")
    private Integer judgeResult;
}
