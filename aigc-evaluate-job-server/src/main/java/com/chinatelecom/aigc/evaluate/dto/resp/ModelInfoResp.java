package com.chinatelecom.aigc.evaluate.dto.resp;

import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.NotEmpty;
import java.time.LocalDateTime;

/**
 * 模型信息 DO
 *
 * @author 后端源码
 */
@Data
@ApiModel(value = "管理后台 - 模型信息 Response Resp")

public class ModelInfoResp {

    @ApiModelProperty(value = "主键 ID", example = "1")
    @TableId
    private Long id;

    @ApiModelProperty(value = "应用名称", required = true, example = "MyApp")
    private String appName;

    @ApiModelProperty(value = "模型名称", required = true, example = "GPT-4")
    private String modelName;

    @ApiModelProperty(value = "模型版本", required = true, example = "1.0.0")
    private String modelVersion;

    @ApiModelProperty(value = "应用可以调用模型的最大并发数", required = true, example = "1")
    private Integer maxThreadSize;

    @ApiModelProperty(value = "模型描述", required = true, example = "这是一个大模型")
    private String modelDescribe;

    @ApiModelProperty(value = "模型 URL", example = "https://example.com/model")
    private String modelUrl;

    @ApiModelProperty(value = "模型路径", example = "/models/gpt-4")
    private String modelPath;

    @ApiModelProperty(value = "模型回调函数", example = "TaskJobHandler")
    private String modelHandler;

    @ApiModelProperty(value = "模型req", example = "telechat-115b")
    private String modelReq;

    @ApiModelProperty(value = "环境参数", required = true, example = "teleai-cloud")
    private String originName;

    @ApiModelProperty(value = "生成词元数量", example = "500")
    private Integer maxCompletionTokens;

    @ApiModelProperty(value = "流式响应", example = "0")
    private Boolean stream;

    @ApiModelProperty(value = "应用 ID", required = true, example = "app-123456")
    private String appid;

    @ApiModelProperty(value = "API Keys（加密存储）", required = true, example = "encrypted_apikey")
    private String apikeys;

    @ApiModelProperty(value = "使用脚本", example = "0")
    private Boolean useScript;

    @ApiModelProperty(value = "脚本语言", example = "python")
    private String scriptLanguage;

    @ApiModelProperty(value = "脚本代码", example = "import a")
    private String scriptSource;

    @ApiModelProperty(value = "创建时间", example = "2025-03-12T12:34:56")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2025-03-12T12:34:56")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
