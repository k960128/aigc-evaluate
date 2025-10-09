package com.chinatelecom.aigc.evaluate.dto.resp;

import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型信息 DO
 *
 * @author 后端源码
 */
@Data
@ApiModel(value = "管理后台 - 模型信息-响应")

public class ModelItemResp {
    @ApiModelProperty(value = "主键 ID", example = "1")
    @TableId
    private Long id;

    @ApiModelProperty(value = "模型名称", required = true, example = "GPT-4")
    private String modelName;

    @ApiModelProperty(value = "模型版本", required = true, example = "v1.0")
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

    @ApiModelProperty(value = "生成词元数量", example = "500")
    private Integer maxCompletionTokens;

    @ApiModelProperty(value = "流式响应", example = "0")
    private Boolean stream;

    @ApiModelProperty(value = "环境参数", required = true, example = "teleai-cloud")
    private String originName;

    @ApiModelProperty(value = "创建时间", example = "2025-03-12T12:34:56")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2025-03-12T12:34:56")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}



