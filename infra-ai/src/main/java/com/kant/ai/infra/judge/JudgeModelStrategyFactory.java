package com.kant.ai.infra.judge;

import com.kant.ai.infra.client.ClientModelInfo;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.framework.exception.ServiceException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 裁判模型策略工厂。
 *
 * <p>通过 Spring 注入全部 JudgeModelStrategy，按厂商构建策略注册表。
 * 后续接入 OpenAI、DeepSeek、GLM Judge 时，只需要新增策略 Bean。</p>
 */
public class JudgeModelStrategyFactory {

    private final Map<ModelManufacturerEnum, JudgeModelStrategy> strategyMap;

    public JudgeModelStrategyFactory(List<JudgeModelStrategy> strategies) {
        this.strategyMap = new EnumMap<>(ModelManufacturerEnum.class);
        for (JudgeModelStrategy strategy : strategies) {
            strategyMap.put(strategy.getManufacturer(), strategy);
        }
    }

    public JudgeModelStrategy getStrategy(ClientModelInfo modelInfo) {
        if (modelInfo == null || modelInfo.getManufacturerType() == null) {
            throw new ServiceException("裁判模型厂商不能为空");
        }
        JudgeModelStrategy strategy = strategyMap.get(modelInfo.getManufacturerType());
        if (strategy == null) {
            throw new ServiceException("不支持的裁判模型厂商：" + modelInfo.getManufacturerType());
        }
        return strategy;
    }
}
