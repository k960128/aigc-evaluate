package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新向量模型信息请求参数。
 *
 * <p>该对象用于接收修改 Embedding 模型配置时提交的数据。id 用于定位需要更新的模型记录，
 * 其余字段表示本次可调整的模型基础信息、连接信息、能力参数和启停状态。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmbeddingModelInfoRequest {

    /** 主键 ID，对应 embedding_model_info 表中的 id，用于定位需要更新的向量模型记录。 */
    private Long id;

    /** 向量模型名称，通常为供应商侧真实模型名称，例如 text-embedding-3-large、bge-m3。 */
    private String model;

    /** 平台内部模型编码，用于业务系统稳定引用该模型，修改时需注意避免影响已有业务配置。 */
    private String modelCode;

    /** 模型厂商编码，对应模型厂商表中的 manufacturer_code，用于区分不同模型供应商。 */
    private String manufacturerCode;

    /** 模型服务基础地址，用于拼接实际的 Embedding 调用接口地址。 */
    private String baseUrl;

    /** 模型服务访问密钥，用于调用供应商接口时进行身份认证。 */
    private String apiKey;

    /** 模型输出的向量维度，例如 768、1024、1536、3072，修改后需与已建向量库集合维度保持一致。 */
    private Integer dimension;

    /** 单次请求允许输入的最大 Token 数，用于调用前做长度控制，避免超过模型限制。 */
    private Integer maxInputTokens;

    /** 单次批量生成向量的最大文本条数，用于控制批量 Embedding 请求规模。 */
    private Integer batchSize;

    /** 推荐的向量距离度量方式，例如 COSINE、L2、IP，用于向量检索时选择相似度计算策略。 */
    private String distanceMetric;

    /** 是否对模型输出向量进行归一化处理，true 表示归一化，false 表示保持模型原始输出。 */
    private Boolean normalize;

    /** 模型描述，用于补充说明模型用途、适用场景、版本特性或接入注意事项。 */
    private String modelDescribe;

    /** 扩展配置 JSON 字符串，用于保存超时时间、重试次数、自定义请求头或供应商特殊参数。 */
    private String config;

    /** 模型状态，false 表示停用，true 表示启用。 */
    private Boolean status;
}
