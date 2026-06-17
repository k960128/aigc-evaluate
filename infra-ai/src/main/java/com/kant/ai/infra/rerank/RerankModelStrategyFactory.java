package com.kant.ai.infra.rerank;

import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.framework.exception.ServiceException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rerank 模型策略工厂。
 *
 * <p>通过 Spring 注入全部 RerankModelStrategy，并按厂商构建策略注册表。
 * 后续新增 OpenAI、DeepSeek、GLM 等 Rerank 厂商时，只需要新增策略 Bean。</p>
 */
public class RerankModelStrategyFactory {

    private final Map<ModelManufacturerEnum, RerankModelStrategy> strategyMap;

    public RerankModelStrategyFactory(List<RerankModelStrategy> strategies) {
        this.strategyMap = new EnumMap<>(ModelManufacturerEnum.class);
        for (RerankModelStrategy strategy : strategies) {
            strategyMap.put(strategy.getManufacturer(), strategy);
        }
    }

    public RerankModelStrategy getStrategy(RerankModelInfo modelInfo) {
        if (modelInfo == null || modelInfo.getManufacturerType() == null) {
            throw new ServiceException("Rerank 模型厂商不能为空");
        }
        RerankModelStrategy strategy = strategyMap.get(modelInfo.getManufacturerType());
        if (strategy == null) {
            throw new ServiceException("不支持的 Rerank 模型厂商：" + modelInfo.getManufacturerType());
        }
        return strategy;
    }
}
