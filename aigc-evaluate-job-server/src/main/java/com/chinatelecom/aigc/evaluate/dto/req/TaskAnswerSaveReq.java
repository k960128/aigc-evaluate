package com.chinatelecom.aigc.evaluate.dto.req;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@ApiModel(value = "管理后台 - 任务答案创建/修改 Request Req")
public class TaskAnswerSaveReq extends PageParam {
    @ApiModelProperty(value = "任务答案 ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "任务 ID", example = "2")
    @NotEmpty(message = "任务 ID 不能为空")
    private String taskId;

    @ApiModelProperty(value = "题目 ID", example = "1000")
    @NotEmpty(message = "题目 ID 不能为空")
    private String questionId;

    @ApiModelProperty(value = "题目版本", example = "1.0")
    @NotEmpty(message = "题目版本不能为空")
    private String questionVersion;

    @ApiModelProperty(value = "任务场景", example = "问答")
    @NotEmpty(message = "任务场景不能为空")
    private String taskScenario;

    @ApiModelProperty(value = "问题集合", example = "测试数据集A")
    @NotEmpty(message = "问题集合不能为空")
    private String questionSet;

    @ApiModelProperty(value = "应用名称", example = "MyApp")
    @NotEmpty(message = "应用名称不能为空")
    private String appName;

    @ApiModelProperty(value = "模型名称", example = "GPT-4")
    @NotEmpty(message = "模型名称不能为空")
    private String modelName;

    @ApiModelProperty(value = "模型版本", example = "1.0.0")
    @NotEmpty(message = "模型版本不能为空")
    private String modelVersion;

    @ApiModelProperty(value = "题目内容", example = "问题")
    @NotEmpty(message = "题目不能为空")
    private String question;

    @ApiModelProperty(value = "答案内容", example = "这是 AI 生成的答案。")
    @NotEmpty(message = "答案不能为空")
    private String answer;

    @ApiModelProperty(value = "评分", example = "4.5")
    private Double score;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
