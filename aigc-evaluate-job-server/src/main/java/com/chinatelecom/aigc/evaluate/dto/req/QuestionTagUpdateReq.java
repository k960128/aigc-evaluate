package com.chinatelecom.aigc.evaluate.dto.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
@ApiModel(value = "管理后台 - 标签编辑 Request Req")
public class QuestionTagUpdateReq {
    /**
     * 主键
     */
    @ApiModelProperty(value = "主键", required = true, example = "1024")
    @NotNull(message = "主键不能为空")
    private Long id;
    /**
     * 主键
     */
    @ApiModelProperty(value = "标签ID", required = true, example = "1901904980932210719")
    @NotNull(message = "标签ID不能为空")
    private String tagId;
    /**
     * 标签名称
     */
    @ApiModelProperty(value = "标签名称", required = true)
    @NotBlank(message = "标签名称不能为空")
    private String tagName;
    /**
     * 标签描述
     */
    @ApiModelProperty(value = "标签描述")
    private String tagDesc;
    /**
     * 父级标签ID
     */
    @ApiModelProperty(value = "父级标签ID,默认0", required = true, example = "1901904980932210719")
    private String parentId;
    /**
     * 所属题库类型
     */
    @ApiModelProperty(value = "所属题库,取值范围：FORWARD-正向题库|NEGATIVE-负向题库", required = true, example = "FORWARD")
    @NotBlank(message = "所属题库不能为空,取值范围：FORWARD-正向题库|NEGATIVE-负向题库")
    @Pattern(regexp = "^(FORWARD|NEGATIVE)$", message = "所属题库取值范围：FORWARD-正向题库|NEGATIVE-负向题库")
    private String category;
}
