package com.chinatelecom.aigc.evaluate.dto.resp;

import com.chinatelecom.aigc.evaluate.dto.model.ExtractConf;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 添加题目集返回 Resp VO")
public class QuestionSetResp {

    @ApiModelProperty(value = "题目集ID")
    private Long id;

    /**
     * 题目集名称
     */
    @ApiModelProperty(value = "题目集名称向", required = true, example = "题目集名称")
    private String questionSetName;

    /**
     * 所选题库
     */
    @ApiModelProperty(value = "所选题库,FORWARD-正向|NEGATIVE-负向", required = true, example = "FORWARD")
    private List<String> questionCategory;

    /**
     * 评测目标
     */
    @ApiModelProperty(value = "评测目标")
    private String evaluationTarget;

    @ApiModelProperty(value = "题目集描述", example = "这是一个题目集")
    private String description;

    /**
     * 抽取题目数量
     */
    @ApiModelProperty(value = "实际抽取题目总数量")
    private Integer extractCount = 0;

    @ApiModelProperty(value = "抽取题目配置信息")
    private ExtractConf extractConf;

    @ApiModelProperty(value = "生成题目正向题库ID集合")
    private List<String> forwardQeustionIdList;

    @ApiModelProperty(value = "生成题目负向题库ID集合")
    private List<String> negativeQeustionIdList;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
