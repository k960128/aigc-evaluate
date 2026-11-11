package com.kant.ai.infra.rerank;

import com.kant.ai.infra.enums.ModelManufacturerEnum;

/**
 * Rerank 默认模型配置。
 *
 * <p>这里保留当前项目已经在使用的 DashScope/Qwen qwen3-rerank 作为兜底配置。
 * 它只会构建本地客户端对象，不会在启动阶段主动发起远程调用。</p>
 */
final class RerankModelDefaults {

    static final String DEFAULT_MODEL_CODE = "qwen3-rerank";

    private RerankModelDefaults() {
    }

    static RerankModelInfo fallbackModelInfo() {
        return RerankModelInfo.builder()
                .modelId(0L)
                .modelCode(DEFAULT_MODEL_CODE)
                .model(DEFAULT_MODEL_CODE)
                .manufacturerType(ModelManufacturerEnum.QWEN)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .apiKey("sk-ws-*****************************************")
                .topN(5)
                .status(true)
                .build();
    }
}
