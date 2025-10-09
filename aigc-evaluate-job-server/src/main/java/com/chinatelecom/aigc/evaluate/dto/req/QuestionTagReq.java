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
@ApiModel(value = "管理后台 - 标签 Request Req")
public class QuestionTagReq {
    /**
     * 所属题库类型
     */
    @ApiModelProperty(value = "所属题库,取值范围：FORWARD-正向题库|NEGATIVE-负向题库", required = true, example = "FORWARD")
    @NotBlank(message = "所属题库不能为空,取值范围：FORWARD-正向题库|NEGATIVE-负向题库")
    @Pattern(regexp = "^(FORWARD|NEGATIVE)$", message = "所属题库取值范围：FORWARD-正向题库|NEGATIVE-负向题库")
    private String category;
}
