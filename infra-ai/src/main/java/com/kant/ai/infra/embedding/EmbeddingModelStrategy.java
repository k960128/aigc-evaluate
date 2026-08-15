package com.kant.ai.infra.embedding;

import com.kant.ai.infra.enums.ModelManufacturerEnum;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Embedding 模型厂商策略接口。
 *
 * <p>不同厂商的 EmbeddingModel 构建差异收敛在策略实现中，RoutingEmbeddingService
 * 只负责路由，不关心底层 SDK 细节。</p>
 */
public interface EmbeddingModelStrategy {

    /**
     * 根据模型配置构建本地 EmbeddingModel 客户端对象。
     */
    EmbeddingModel buildEmbeddingModel(EmbeddingModelInfo modelInfo);

    /**
     * 当前策略支持的模型厂商。
     */
    ModelManufacturerEnum getManufacturer();
}
