package com.chinatelecom.aigc.evaluate.dto.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 添加题目集映射返回 Resp VO")
public class QuestionSetItemResp {

    @ApiModelProperty(value = "题目集ID")
    private Long id;

    @ApiModelProperty(value = "题目集ID")
    private Long questionSetId;

    /**
     * 题目集名称
     */
    @ApiModelProperty(value = "题目集名称向", required = true, example = "题目集名称")
    private String questionSetName;

    /**
     * 题目唯一ID
     */
    @ApiModelProperty(value = "题目唯一ID")
    private String questionId;

    /**
     * 题目唯一ID
     */
    @ApiModelProperty(value = "题目版本")
    private Integer questionVersion;

    /**
     * 题目内容
     */
    @ApiModelProperty(value = "题目内容")
    private String title;

    /**
     * 所属题库
     */
    @ApiModelProperty(value = "所属题库")
    private String category;

    /**
     * 标签
     */
    @ApiModelProperty(value = "标签")
    private String tags;

    /**
     * 题目难度
     */
    @ApiModelProperty(value = "题目难度")
    private String difficulty;

    /**
     * 攻击方式
     */
    @ApiModelProperty(value = "攻击方式")
    private String attackMethod;
    /**
     * 数据来源
     */
    @ApiModelProperty(value = "数据来源")
    private String dataSource;
}
