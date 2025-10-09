package com.chinatelecom.aigc.evaluate.dto.resp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 任务答案分页 Resp VO")
public class TaskAnswerPageResp {

    @ApiModelProperty(value = "模型信息列表")
    private List<TaskAnswerModelResp> modelInfo;

    @ApiModelProperty(value = "任务ID")
    private Long taskId;

    @ApiModelProperty(value = "题目唯一编号")
    private String questionId;

    @ApiModelProperty(value = "题目版本号")
    private Integer questionVersion;

    @ApiModelProperty(value = "题目内容")
    private String questionContent;

    @ApiModelProperty(value = "所属题库")
    private String questionCategory;
}

