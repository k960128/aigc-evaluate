package com.kant.ai.infra.judge;

import com.kant.ai.infra.client.ModelConnectionResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;

/**
 * 裁判大模型策略接口。
 *
 * <p>不同厂商的 Judge 调用差异收敛在 infra-ai 内部，eval 模块只需要依赖 JudgeModelClient。</p>
 */
public interface JudgeModelStrategy {

    /**
     * 调用裁判大模型，返回未经业务解析的原始响应。
     */
    JudgeModelResponse judge(JudgeModelRequest request);

    /**
     * 裁判模型连通性探测，主要用于后续管理端或运维检查。
     */
    ModelConnectionResponse connection(JudgeModelRequest request);

    /**
     * 当前策略支持的模型厂商。
     */
    ModelManufacturerEnum getManufacturer();
}
