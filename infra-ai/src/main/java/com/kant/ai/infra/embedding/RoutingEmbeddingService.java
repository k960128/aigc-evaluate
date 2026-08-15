package com.kant.ai.infra.embedding;

import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

/**
 * 路由型 Embedding 服务。
 *
 * <p>默认调用启动初始化后的默认模型；传入模型 ID 或 modelCode 时，按注册表路由到指定模型。</p>
 */
public class RoutingEmbeddingService implements EmbeddingService {

    private final EmbeddingModelRegistry embeddingModelRegistry;

    public RoutingEmbeddingService(EmbeddingModelRegistry embeddingModelRegistry) {
        this.embeddingModelRegistry = embeddingModelRegistry;
    }

    @Override
    public float[] embed(String text) {
        return getEmbeddingClient().embed(text);
    }

    @Override
    public float[] embed(String text, String modelId) {
        return embeddingModelRegistry.getEmbeddingModel(modelId).embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return getEmbeddingClient().embed(texts);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, String modelId) {
        return embeddingModelRegistry.getEmbeddingModel(modelId).embed(texts);
    }

    @Override
    public int dimension() {
        return embeddingModelRegistry.getDefaultDimension();
    }

    @Override
    public EmbeddingModel getEmbeddingClient() {
        return embeddingModelRegistry.getDefaultEmbeddingModel();
    }
}
