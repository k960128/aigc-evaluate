package com.chinatelecom.aigc.evaluate.dto.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotEmpty;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
@ApiModel(value = "管理后台 - 模型信息创建")
public class ModelInfoSaveReq {
    @ApiModelProperty(value = "应用名称", example = "telechat")
    @NotEmpty(message = "应用名称不能为空")
    private String appName;

    @ApiModelProperty(value = "模型名称", example = "xinghe")
    @NotEmpty(message = "模型名称不能为空")
    private String modelName;

    @ApiModelProperty(value = "模型版本", example = "v1.0")
    @NotEmpty(message = "模型版本不能为空")
    private String modelVersion;

    @ApiModelProperty(value = "最大并发数", example = "1")
    private Integer maxThreadSize;

    @ApiModelProperty(value = "模型描述", example = "星河大模型")
    private String modelDescribe;

    @ApiModelProperty(value = "模型 URL ", example = "https://openapi.teleagi.cn")
    //@NotEmpty(message = "模型 URL 不能为空")
    private String modelUrl;

    @ApiModelProperty(value = "模型路径", example = "/aipaas/lm/v1/x/telechatCompletions")
    //@NotEmpty(message = "模型路径不能为空")
    private String modelPath;

    @ApiModelProperty(value = "模型req", example = "telechat-115b")
    //@NotEmpty(message = "模型req不能为空")
    private String modelReq;

    @ApiModelProperty(value = "模型处理函数", example = "taskJobHandler")
    //@NotEmpty(message = "模型处理函数不能为空")
    private String modelHandler;

    @ApiModelProperty(value = "环境参数", example = "teleai-cloud-auth-v1")
    //@NotEmpty(message = "环境参数不能为空")
    private String originName;

    @ApiModelProperty(value = "生成词元数量", example = "500")
    private Integer maxCompletionTokens;

    @ApiModelProperty(value = "流式响应", example = "0")
    private Boolean stream;

    @ApiModelProperty(value = "应用 ID", example = "c1255abcaf654ca98d31f6f7b231168c")
    //@ApiModelProperty(value = "应用 ID ", example = "c1255abcaf654ca98d31f6f7b231168c")
    private String appid;

    @ApiModelProperty(value = "API Keys", example = "api keys")
    //@NotEmpty(message = "API Keys 不能为空")
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
}

