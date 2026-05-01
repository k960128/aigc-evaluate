package com.kant.llm.eval.client;

import com.kant.llm.eval.common.enums.ModelManufacturerEnum;

/**
 * 大模型策略接口
 */
public interface ModelClientStrategy {

    /**
     * 调用大模型
     * @param modelRequest 模型请求参数
     * @return 模型响应结果
     */
    ModelResponse call(ModelRequest modelRequest);

    /**
     * 大模型厂商标识
     */
    ModelManufacturerEnum getManufacturer();

    /**
     * 连通性测试
     * @return 连通性测试结果
     */
    ModelConnectionResponse connection(ModelRequest modelRequest);
}
