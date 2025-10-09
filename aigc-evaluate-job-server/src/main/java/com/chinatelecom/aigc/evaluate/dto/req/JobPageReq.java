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
@ApiModel(value = "管理后台 - 定时任务分页 Request Req")
public class JobPageReq extends PageParam {

    @ApiModelProperty("任务名称，模糊匹配")
    private String name;

    @ApiModelProperty("任务状态，开启-1|暂停-2")
    private Integer status;

    @ApiModelProperty("模型名称，模糊匹配")
    private String modelName;
    /*
    @ApiModelProperty("处理器的名字，模糊匹配")
    private String handlerName;
    */
}
