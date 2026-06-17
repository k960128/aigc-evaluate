package com.kant.ai.infra.judge;

import com.kant.ai.infra.client.ClientModelInfo;
import com.kant.ai.infra.client.ModelConnectionResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;

/**
 * 裁判模型统一入口。
 *
 * <p>该类负责补齐默认裁判模型配置、选择具体厂商策略，并把原始响应交还给上层 L3 解析。</p>
 */
public class JudgeModelClient {

    /**
     * 当前阶段沿用原有裁判模型默认配置，后续可迁移到配置中心或数据库。
     */
    private static final ClientModelInfo DEFAULT_JUDGE_MODEL_INFO = ClientModelInfo.builder()
            .manufacturerType(ModelManufacturerEnum.QWEN)
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
            .apiKey("sk-3398a71a31f04728a19922dcbb8af1f9")
            .model("qwen-plus")
            .build();

    private final JudgeModelStrategyFactory judgeModelStrategyFactory;

    public JudgeModelClient(JudgeModelStrategyFactory judgeModelStrategyFactory) {
        this.judgeModelStrategyFactory = judgeModelStrategyFactory;
    }

    /**
     * 调用真实裁判模型。
     */
    public JudgeModelResponse judge(JudgeModelRequest request) {
        JudgeModelRequest actualRequest = normalizeRequest(request);
        JudgeModelStrategy strategy = judgeModelStrategyFactory.getStrategy(actualRequest.getClientModelInfo());
        return strategy.judge(actualRequest);
    }

    /**
     * 裁判模型连通性检查。
     */
    public ModelConnectionResponse connection(JudgeModelRequest request) {
        JudgeModelRequest actualRequest = normalizeRequest(request);
        JudgeModelStrategy strategy = judgeModelStrategyFactory.getStrategy(actualRequest.getClientModelInfo());
        return strategy.connection(actualRequest);
    }

    /**
     * 兼容上层未传模型配置的场景，保证 L3 real 模式仍可沿用默认 Qwen 裁判模型。
     */
    private JudgeModelRequest normalizeRequest(JudgeModelRequest request) {
        if (request == null) {
            return JudgeModelRequest.builder()
                    .clientModelInfo(DEFAULT_JUDGE_MODEL_INFO)
                    .build();
        }
        request.setClientModelInfo(resolveClientModelInfo(request.getClientModelInfo()));
        return request;
    }

    /**
     * 对空字段做默认值填充，避免上层只传 prompt 时无法完成裁判调用。
     */
    private ClientModelInfo resolveClientModelInfo(ClientModelInfo modelInfo) {
        if (modelInfo == null) {
            return DEFAULT_JUDGE_MODEL_INFO;
        }
        if (modelInfo.getManufacturerType() == null) {
            modelInfo.setManufacturerType(DEFAULT_JUDGE_MODEL_INFO.getManufacturerType());
        }
        if (isBlank(modelInfo.getBaseUrl())) {
            modelInfo.setBaseUrl(DEFAULT_JUDGE_MODEL_INFO.getBaseUrl());
        }
        if (isBlank(modelInfo.getApiKey())) {
            modelInfo.setApiKey(DEFAULT_JUDGE_MODEL_INFO.getApiKey());
        }
        if (isBlank(modelInfo.getModel())) {
            modelInfo.setModel(DEFAULT_JUDGE_MODEL_INFO.getModel());
        }
        return modelInfo;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
