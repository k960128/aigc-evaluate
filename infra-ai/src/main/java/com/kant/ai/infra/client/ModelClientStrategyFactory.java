package com.kant.ai.infra.client;

import com.kant.ai.infra.client.strategy.*;
import com.kant.framework.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端策略工厂
 */
@Slf4j
public class ModelClientStrategyFactory {

    private final Map<Long, ModelClientStrategy> strategyMap = new ConcurrentHashMap<>();

    /**
     * 根据大模型厂商标识获取模型客户端策略
     */
    public ModelClientStrategy getStrategy(ClientModelInfo modelInfo) {
        return strategyMap.computeIfAbsent(modelInfo.getModelId(),
                (strategy) -> createStrategy(modelInfo));
    }

    private ModelClientStrategy createStrategy(ClientModelInfo modelInfo) {
        return switch (modelInfo.getManufacturerType()) {
            case OPENAI -> new OpenAiModelClientStrategy();
            case DEEPSEEK -> new DeepSeekModelClientStrategy();
            case QWEN -> new QwenModelClientStrategy();
            case SPARK -> new SparkModelClientStrategy();
            case GLM -> new GlmModelClientStrategy();
            case GPT -> new GptModelClientStrategy();
            case MOCK -> new MockModelClientStrategy();
            default -> throw new ServiceException("不支持的大模型厂商标识：" + modelInfo.getManufacturerType());
        };
    }
}
