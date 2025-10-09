package com.chinatelecom.aigc.evaluate.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 模型信息 DO
 *
 * @author 后端源码
 */
@TableName("model_item")
@KeySequence("model_item_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelItemDO {

    /** 主键 ID */
    @TableId
    private Long id;

    /** 模型名称 */
    private String modelName;

    /** 模型版本 */
    private String modelVersion;

    /** 应用可以调用模型的最大并发数 */
    private Integer maxThreadSize;

    /** 模型描述 */
    private String modelDescribe;

    /** 模型 URL */
    private String modelUrl;

    /** 模型路径 */
    private String modelPath;

    /** 模型回调函数 */
    private String modelHandler;

    /** 模型路径 */
    private String modelReq;

    /** 环境参数 */
    private String originName;

    /** 生成词元数量 */
    private Integer maxCompletionTokens;

    /** 流式响应 */
    private Boolean stream;

    /** 创建时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
