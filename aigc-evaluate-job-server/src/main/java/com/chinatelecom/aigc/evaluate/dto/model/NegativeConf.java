package com.chinatelecom.aigc.evaluate.dto.model;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
@ApiModel(value = "管理后台 - NegativeConf Request Req")
public class NegativeConf {

    @ApiModelProperty(value = "自定义难度配置")
    private ExtractConfDifficulty difficultyConf;
    @ApiModelProperty(value = "随机配置")
    private ExtractConfRandom randomConf;
    @ApiModelProperty(value = "自定义题目配置")
    private ExtractConfCustom customConf;
}
