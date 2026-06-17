package com.kant.ai.infra.embedding;

import com.kant.ai.infra.enums.ModelManufacturerEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedding 模型运行配置。
 *
 * <p>该对象是 infra-ai 内部的轻量模型，不直接绑定任何业务表结构。
 * eval 模块可从 embedding_model_info 表读取数据后转换成该对象。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingModelInfo {

    /** 模型主键 ID。 */
    private Long modelId;

    /** 供应商侧真实 Embedding 模型名称。 */
    private String model;

    /** 平台内部稳定模型编码，可用于运行期路由。 */
    private String modelCode;

    /** 模型服务访问密钥。 */
    private String apiKey;

    /** 模型服务基础地址。 */
    private String baseUrl;

    /** Embedding 向量维度。 */
    private Integer dimension;

    /** 单次输入最大 token 数。 */
    private Integer maxInputTokens;

    /** 单次批量向量化最大文本条数。 */
    private Integer batchSize;

    /** 推荐向量距离度量方式。 */
    private String distanceMetric;

    /** 是否对模型输出向量做归一化。 */
    private Boolean normalize;

    /** 扩展 JSON 配置。 */
    private String config;

    /** 是否启用。 */
    private Boolean status;

    /** 模型厂商标识。 */
    private ModelManufacturerEnum manufacturerType;
}
