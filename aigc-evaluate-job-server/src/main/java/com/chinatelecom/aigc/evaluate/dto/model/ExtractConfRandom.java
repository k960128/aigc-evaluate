package com.chinatelecom.aigc.evaluate.dto.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
@ApiModel(value = "管理后台 - ExtractConfRandom Request Req")
public class ExtractConfRandom {

    /**
     * 抽取题目数量
     */
    @ApiModelProperty(value = "随机-抽取题目数量")
    private Integer randomCount = 0;
}
