package com.chinatelecom.aigc.evaluate.dto.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 标签 Resp VO")
public class QuestionTagResp {

    @ApiModelProperty(value = "主键")
    private Long id;
    /**
     * 标签ID
     */
    @ApiModelProperty(value = "标签ID")
    private String tagId;

    /**
     * 标签名称
     */
    @ApiModelProperty(value = "标签名称")
    private String tagName;

    /**
     * 标签描述
     */
    @ApiModelProperty(value = "标签描述")
    private String tagDesc;

    /**
     * 标签级别
     */
    @ApiModelProperty(value = "标签级别")
    private Integer tagLevel;

    /**
     * 标签父级ID
     */
    @ApiModelProperty(value = "标签父级ID")
    private String parentId;

    /**
     * 标签类型
     */
    @ApiModelProperty(value = "标签类型")
    private String type;

    /**
     * 子标签数量
     */
    @ApiModelProperty(value = "子标签数量")
    private Integer count;

    /**
     * 包含题目数量
     */
    @ApiModelProperty(value = "包含题目数量", example = "0")
    private Integer questionCount;

    @ApiModelProperty(value = "子类集合")
    private List<QuestionTagResp> child;
}
