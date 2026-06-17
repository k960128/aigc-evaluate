package com.kant.ai.infra.rerank;

import com.kant.ai.infra.enums.ModelManufacturerEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rerank 模型运行配置。
 *
 * <p>该对象只描述 infra-ai 调用重排模型所需的通用信息，不绑定具体业务表。
 * 后续如果要把 Rerank 模型配置放入数据库，只需要新增一个 RerankModelInfoProvider
 * 将数据库记录转换成该对象即可。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankModelInfo {

    /** 模型主键 ID，可用于运行期路由。 */
    private Long modelId;

    /** 平台内部稳定模型编码，可用于运行期路由。 */
    private String modelCode;

    /** 厂商侧真实 Rerank 模型名称。 */
    private String model;

    /** 模型服务访问密钥。 */
    private String apiKey;

    /** 模型服务基础地址，部分厂商 SDK 暂不需要使用该字段。 */
    private String baseUrl;

    /** Rerank 返回的候选 TopN。 */
    private Integer topN;

    /** 扩展 JSON 配置，预留给不同厂商的个性化参数。 */
    private String config;

    /** 是否启用。 */
    private Boolean status;

    /** 模型厂商标识。 */
    private ModelManufacturerEnum manufacturerType;
}
