package com.kant.ai.infra.rerank;

import com.alibaba.cloud.ai.model.RerankResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 Rerank 响应结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankServiceResult {

    /** Rerank 模型原始响应，业务模块可按自身语义继续转换。 */
    private RerankResponse rawResponse;

    /** 是否发生降级。 */
    private Boolean degraded;

    /** 降级或异常原因。 */
    private String reason;

    /** Rerank 模型调用耗时，单位毫秒。 */
    private Long elapsed;

    /** 实际命中的 Rerank 模型厂商。 */
    private ModelManufacturerEnum manufacturer;

    /** 实际调用的 Rerank 模型名称。 */
    private String model;

    /** 实际调用的 Rerank 模型编码。 */
    private String modelCode;
}
