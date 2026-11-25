package com.kant.ai.infra.client;

import com.kant.ai.infra.client.strategy.*;
import com.kant.framework.exception.ServiceException;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端策略工厂
 */
@Slf4j
public class ModelClientStrategyFactory {

    private final Map<Long, ModelClientStrategy> strategyMap = new ConcurrentHashMap<>();
    private final ObservationRegistry observationRegistry;

    public ModelClientStrategyFactory(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    /**
     * 根据大模型厂商标识获取模型客户端策略
     */
    public ModelClientStrategy getStrategy(ClientModelInfo modelInfo) {
        // 判断是否需要缓存ModelInfo。 测试连通性不做缓存
        if (modelInfo.getType().equals("TEST_CONNECTION")) {
            return createStrategy(modelInfo);
        }
        return strategyMap.compute(modelInfo.getModelId(), (modelId, currentStrategy) -> {
            // 模型配置允许修改厂商。缓存中的策略与当前快照不一致时必须重建，
            // 避免使用旧厂商客户端执行已经固定为新版本的评测目标。
            if (currentStrategy == null
                    || currentStrategy.getManufacturer() != modelInfo.getManufacturerType()) {
                return createStrategy(modelInfo);
            }
            return currentStrategy;
        });
    }

    private ModelClientStrategy createStrategy(ClientModelInfo modelInfo) {
        return switch (modelInfo.getManufacturerType()) {
            case OPENAI -> new OpenAiModelClientStrategy(observationRegistry);
            case DEEPSEEK -> new DeepSeekModelClientStrategy(observationRegistry);
            case QWEN -> new QwenModelClientStrategy(observationRegistry);
            case SPARK -> new SparkModelClientStrategy();
            case GLM -> new GlmModelClientStrategy(observationRegistry);
            case GPT -> new GptModelClientStrategy(observationRegistry);
            case MOCK -> new MockModelClientStrategy();
            case OLLAMA -> new OllamaModelClientStrategy(observationRegistry);
            case GROK -> new GrokModelClientStrategy(observationRegistry);
            default -> throw new ServiceException("不支持的大模型厂商标识：" + modelInfo.getManufacturerType());
        };
    }
}
