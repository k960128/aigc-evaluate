package com.chinatelecom.aigc.evaluate.dto.req;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 模型信息管理分页-请求")
public class ModelItemPageReq extends PageParam {
    @ApiModelProperty(value = "模型名称，模糊匹配", example = "GPT-4")
    private String modelName;

    @ApiModelProperty(value = "模型版本，模糊匹配", example = "v1.0")
    private String modelVersion;
}
