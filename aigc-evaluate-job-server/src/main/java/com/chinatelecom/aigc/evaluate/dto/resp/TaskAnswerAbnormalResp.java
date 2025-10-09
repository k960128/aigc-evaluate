package com.chinatelecom.aigc.evaluate.dto.resp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class TaskAnswerAbnormalResp {
    @ApiModelProperty(value = "模型编号")
    private Long modelId;

    @ApiModelProperty(value = "模型名字")
    private String modelName;

    @ApiModelProperty(value = "模型版本")
    private String modelVersion;

    @ApiModelProperty(value = "题目标题")
    private String questionContent;

    @ApiModelProperty(value = "题目答案")
    private String answerContent;

    @ApiModelProperty(value = "应用名称")
    private String appName;

    @ApiModelProperty(value = "人工审核结果")
    private Integer judgeResult;

    @ApiModelProperty(value = "设置自动判定结果")
    private Integer violation;

    @ApiModelProperty(value = "推理过程")
    private String thinkProcess;

    @ApiModelProperty(value = "一级标签")
    private String firstTag;
    @ApiModelProperty(value = "二级标签")
    private String secondTag;

    @ApiModelProperty(value = "所属题库")
    private String questionCategory;
}
