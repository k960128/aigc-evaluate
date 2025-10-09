package com.chinatelecom.aigc.evaluate.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@ApiModel(value = "管理后台 - 模型信息创建/修改 Request Req")
public class ModelInfoUpdateReq {

    @ApiModelProperty(value = "模型 ID", example = "1")
    @NotNull(message = "ID 不能为空")
    private Long id;

    @ApiModelProperty(value = "应用名称", example = "telechat")
    private String appName;

    @ApiModelProperty(value = "模型名称", example = "星河")
    private String modelName;

    @ApiModelProperty(value = "模型版本", example = "v1.0")
    private String modelVersion;

    @ApiModelProperty(value = "最大并发数", example = "1")
    private Integer maxThreadSize;

    @ApiModelProperty(value = "模型描述", example = "星河大模型")
    private String modelDescribe;

    @ApiModelProperty(value = "模型 URL ", example = "http://127.0.0.1")
    private String modelUrl;

    @ApiModelProperty(value = "模型路径", example = "/path")
    private String modelPath;

    @ApiModelProperty(value = "环境参数", example = "telechat-req")
    private String originName;

    @ApiModelProperty(value = "生成词元数量", example = "500")
    private Integer maxCompletionTokens;

    @ApiModelProperty(value = "流式响应", example = "0")
    private Boolean stream;

    @ApiModelProperty(value = "应用 ID ", example = "12345")
    private String appid;

    @ApiModelProperty(value = "API Keys", example = "54321")
    private String apikeys;

    @ApiModelProperty(value = "使用脚本", example = "0")
    //@NotEmpty(message = "脚本语言不能为空")
    private Boolean useScript;

    @ApiModelProperty(value = "脚本语言", example = "python")
    //@NotEmpty(message = "脚本语言不能为空")
    private String scriptLanguage;

    @ApiModelProperty(value = "脚本代码", example = "import a")
    //@NotEmpty(message = "脚本代码不能为空")
    private String scriptSource;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}

