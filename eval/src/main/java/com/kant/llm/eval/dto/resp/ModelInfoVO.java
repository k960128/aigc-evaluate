package com.kant.llm.eval.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模型信息 BO
 *
 * @author 后端源码
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoVO {
    /** 主键 ID */
    private Long id;

    /** 模型名称 */
    private String model;

    /** 模型url */
    private String baseUrl;

    /** 应用密钥 */
    private String apiKey;

    /** 模型描述 */
    private String modelDescribe;

    /** 最大并发数 */
    private Long maxThreadSize;

    /** 环境参数 */
    private String originName;

    /** 生成词元数量 */
    private Long maxCompletionTokens;

    /** 流式响应 默认0，非流式 */
    private Boolean stream;

    /** 扩展配置 */
    private Map<String, Object> config;

    /** 版本号 */
    private Long version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}