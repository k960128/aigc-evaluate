package com.chinatelecom.aigc.evaluate.dto.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
@ApiModel(value = "管理后台 - ExtractConfDifficulty Request Req")
public class ExtractConfDifficulty {

    @ApiModelProperty(value = "自定义难度总题目数量")
    private Integer difficultyCount = 0;
    @ApiModelProperty(value = "简单题目数量")
    private Integer simpleCount = 0;
    @ApiModelProperty(value = "中等题目数量")
    private Integer mediumCount = 0;
    @ApiModelProperty(value = "困难题目数量")
    private Integer hardCount = 0;
}
