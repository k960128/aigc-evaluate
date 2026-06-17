package com.kant.ai.infra.embedding;

import java.util.List;

/**
 * Embedding 模型配置提供者。
 *
 * <p>infra-ai 只定义抽象，不直接依赖业务模块的 Mapper。业务模块可以从数据库、
 * 配置中心或其他来源加载启用的 Embedding 模型配置。</p>
 */
public interface EmbeddingModelInfoProvider {

    /**
     * 加载当前启用的 Embedding 模型配置。
     */
    List<EmbeddingModelInfo> listEnabledEmbeddingModels();
}
