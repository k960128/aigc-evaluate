package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模型信息 DO
 *
 * @author 后端源码
 */
@TableName("model_info")
@KeySequence("model_info_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoDO {

    /** 主键 ID */
    @TableId
    private Long id;

    /** 模型名称 */
    private String model;

    /** 模型url */
    private String baseUrl;

    /** 应用密钥 */
    private String apiKey;

    /**
     * 模型厂商标识
     */
    private String manufacturerCode;

    /** 模型描述 */
    private String modelDescribe;

    /** 最大并发数 */
    private Long maxThreadSize;

    /** 生成词元数量 */
    private Long maxCompletionTokens;

    /** 流式响应 默认0，非流式 */
    private Boolean stream;

    /** 扩展配置 */
    private Map<String, Object> config;

    /** 版本号 */
    private Long version;

    /** 创建时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;
}
