package com.chinatelecom.aigc.evaluate.dto.req;

import com.chinatelecom.aigc.evaluate.dto.model.ExtractConf;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractConfCustom;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
@ApiModel(value = "管理后台 - 题目创建集合 QuestionSetUpdateReq Req")
public class QuestionSetUpdateReq {

    @ApiModelProperty(value = "题目集主键ID", required = true, example = "1")
    @NotNull(message = "题目集主键ID不能为空")
    private Long id;
    /**
     * 题目集名称
     */
    @ApiModelProperty(value = "题目集名称向", required = true, example = "题目集名称")
    @NotBlank(message = "题目集名称不能为空")
    private String questionSetName;

    /**
     * 所选题库
     */
    @ApiModelProperty(value = "所选题库,FORWARD-正向|NEGATIVE-负向", required = true, example = "[\"FORWARD\"]")
    @NotEmpty(message = "所选题库不能为空")
    private List<String> questionCategory;

    @ApiModelProperty(value = "评测目标", example = "1024")
    private String evaluationTarget;

    @ApiModelProperty(value = "题目集描述", example = "这是一个题目集")
    private String description;

    /**
     * 抽取题目维度配置
     */
    @ApiModelProperty(value = "抽取题目维度配置", required = true, example = "1024")
    @NotNull(message = "抽取题目维度配置不能为空")
    private ExtractConf extractConf;
}
