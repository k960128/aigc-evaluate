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
@ApiModel(value = "管理后台 - ExtractConf Request Req")
public class ExtractConf {

    @ApiModelProperty(value = "正向题库抽题配置信息")
    private ForwardConf forwardConf;

    @ApiModelProperty(value = "负向题库抽题配置信息")
    private NegativeConf negativeConf;
}
