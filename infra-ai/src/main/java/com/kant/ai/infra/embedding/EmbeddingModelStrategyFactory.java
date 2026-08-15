package com.kant.ai.infra.embedding;

import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.framework.exception.ServiceException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding 模型策略工厂。
 *
 * <p>通过 Spring 注入全部 EmbeddingModelStrategy，按厂商构建策略注册表。
 * 后续扩展 OpenAI、DeepSeek、GLM 等厂商时，只需要新增策略 Bean。</p>
 */
public class EmbeddingModelStrategyFactory {

    private final Map<ModelManufacturerEnum, EmbeddingModelStrategy> strategyMap;

    public EmbeddingModelStrategyFactory(List<EmbeddingModelStrategy> strategies) {
        this.strategyMap = new EnumMap<>(ModelManufacturerEnum.class);
        for (EmbeddingModelStrategy strategy : strategies) {
            strategyMap.put(strategy.getManufacturer(), strategy);
        }
    }

    public EmbeddingModelStrategy getStrategy(EmbeddingModelInfo modelInfo) {
        if (modelInfo == null || modelInfo.getManufacturerType() == null) {
            throw new ServiceException("Embedding 模型厂商不能为空");
        }
        EmbeddingModelStrategy strategy = strategyMap.get(modelInfo.getManufacturerType());
        if (strategy == null) {
            throw new ServiceException("不支持的 Embedding 模型厂商：" + modelInfo.getManufacturerType());
        }
        return strategy;
    }
}
