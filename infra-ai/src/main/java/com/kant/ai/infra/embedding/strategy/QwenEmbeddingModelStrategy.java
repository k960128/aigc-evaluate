package com.kant.ai.infra.embedding.strategy;

import com.kant.ai.infra.embedding.EmbeddingModelInfo;
import com.kant.ai.infra.embedding.EmbeddingModelStrategy;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Qwen Embedding 模型策略。
 *
 * <p>DashScope 提供 OpenAI compatible 协议，因此这里复用 Spring AI OpenAiEmbeddingModel。</p>
 */
public class QwenEmbeddingModelStrategy implements EmbeddingModelStrategy {

    private static final int DEFAULT_DIMENSION = 1024;

    @Override
    public EmbeddingModel buildEmbeddingModel(EmbeddingModelInfo modelInfo) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(modelInfo.getApiKey())
                .baseUrl(modelInfo.getBaseUrl())
                .build();

        OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(modelInfo.getModel())
                .dimensions(resolveDimension(modelInfo))
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
    }

    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.QWEN;
    }

    private int resolveDimension(EmbeddingModelInfo modelInfo) {
        if (modelInfo.getDimension() == null || modelInfo.getDimension() <= 0) {
            return DEFAULT_DIMENSION;
        }
        return modelInfo.getDimension();
    }
}
