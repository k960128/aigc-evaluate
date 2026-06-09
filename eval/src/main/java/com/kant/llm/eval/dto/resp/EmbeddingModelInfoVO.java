package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 向量模型信息响应对象。
 *
 * <p>该对象用于向前端或平台内部调用方返回 Embedding 模型的完整展示信息，
 * 包括模型基础属性、调用连接信息、向量能力参数、启停状态以及记录创建和更新时间。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingModelInfoVO {

    /** 主键 ID，对应 embedding_model_info 表中的 id。 */
    private Long id;

    /** 向量模型名称，通常为供应商侧真实模型名称，例如 text-embedding-3-large、bge-m3。 */
    private String model;

    /** 平台内部模型编码，用于业务系统稳定引用该模型。 */
    private String modelCode;

    /** 模型厂商编码，对应模型厂商表中的 manufacturer_code。 */
    private String manufacturerCode;

    /** 模型服务基础地址，用于拼接实际的 Embedding 调用接口地址。 */
    private String baseUrl;

    /** 模型服务访问密钥，用于调用供应商接口时进行身份认证。 */
    private String apiKey;

    /** 模型输出的向量维度，例如 768、1024、1536、3072。 */
    private Integer dimension;

    /** 单次请求允许输入的最大 Token 数。 */
    private Integer maxInputTokens;

    /** 单次批量生成向量的最大文本条数。 */
    private Integer batchSize;

    /** 推荐的向量距离度量方式，例如 COSINE、L2、IP。 */
    private String distanceMetric;

    /** 是否对模型输出向量进行归一化处理，true 表示归一化，false 表示保持模型原始输出。 */
    private Boolean normalize;

    /** 模型描述，用于补充说明模型用途、适用场景、版本特性或接入注意事项。 */
    private String modelDescribe;

    /** 扩展配置 JSON 字符串，用于保存超时时间、重试次数、自定义请求头或供应商特殊参数。 */
    private String config;

    /** 模型状态，false 表示停用，true 表示启用。 */
    private Boolean status;

    /** 版本号，用于配置变更追踪或后续乐观锁控制。 */
    private Long version;

    /** 创建时间，表示该向量模型记录首次创建的时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间，表示该向量模型记录最近一次被修改的时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
