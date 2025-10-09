package com.chinatelecom.aigc.evaluate.dto.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
@ApiModel(value = "管理后台 - 题目创建 Request Req")
public class QuestionSaveReq {

    private Long id;

    private String questionId;
    /**
     * 题目内容
     */
    @ApiModelProperty(value = "题目内容", required = true, example = "题目1")
    @NotBlank(message = "题目编号不能为空")
    private String title;
    /**
     * 所属题库
     * FORWARD-正向题库
     * NEGATIVE-负向题库
     */
    @ApiModelProperty(value = "所属题库,取值范围：FORWARD-正向题库|NEGATIVE-负向题库", required = true, example = "FORWARD")
    @NotBlank(message = "所属题库不能为空,取值范围：FORWARD-正向题库|NEGATIVE-负向题库")
    @Pattern(regexp = "^(FORWARD|NEGATIVE)$", message = "所属题库取值范围：FORWARD-正向题库|NEGATIVE-负向题库")
    private String category;
    /**
     * 标签
     */
    @ApiModelProperty(value = "标签")
    private String tags;
    /**
     * 题目难度
     */
    @ApiModelProperty(value = "题目难度，取值范围：SIMPLE-简单|MEDIUM-中等|HARD-困难", required = true, example = "SIMPLE")
    @NotBlank(message = "题目难度不能为空,取值范围：SIMPLE-简单|MEDIUM-中等|HARD-困难")
    @Pattern(regexp = "^(SIMPLE|MEDIUM|HARD)$", message = "题目难度取值范围：SIMPLE-简单|MEDIUM-中等|HARD-困难")
    private String difficulty;
    /**
     * 攻击方式
     */
    @ApiModelProperty(value = "攻击方式", example = "无")
    private String attackMethod;

    /**
     * 数据来源
     */
    @ApiModelProperty(value = "数据来源")
    private String dataSource;

    @ApiModelProperty(value = "题目描述")
    private String desc;

    private String createTime;

    private String updateTime;

    private String contentHash;
}
