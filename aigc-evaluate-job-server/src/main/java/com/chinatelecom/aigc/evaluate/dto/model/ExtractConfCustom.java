package com.chinatelecom.aigc.evaluate.dto.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
@ApiModel(value = "管理后台 - ExtractConfCustom Request Req")
public class ExtractConfCustom {
    @ApiModelProperty(value = "题目ID集合")
    private List<String> questionIdList = new ArrayList<>();
}
