package com.kant.llm.eval.client;
import com.kant.llm.eval.client.strategy.DeepSeekModelClientStrategy;
import com.kant.llm.eval.client.strategy.GlmModelClientStrategy;
import com.kant.llm.eval.client.strategy.OpenAiModelClientStrategy;
import com.kant.llm.eval.client.strategy.QwenModelClientStrategy;
import com.kant.llm.eval.client.strategy.SparkModelClientStrategy;
import com.kant.llm.eval.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端策略工厂
 */
@Service
public class ModelClientStrategyFactory {

    private final Map<Long, ModelClientStrategy> strategyMap = new ConcurrentHashMap<>();

    /**
     * 根据大模型厂商标识获取模型客户端策略
     */
    public ModelClientStrategy getStrategy(ModelInfo modelInfo) {
        return strategyMap.compute(modelInfo.getModelId(),
                (key, strategy) -> createStrategy(modelInfo));
    }

    private ModelClientStrategy createStrategy(ModelInfo modelInfo) {
        return switch (modelInfo.getManufacturerType()) {
            case OPENAI -> new OpenAiModelClientStrategy();
            case DEEPSEEK -> new DeepSeekModelClientStrategy();
            case QWEN -> new QwenModelClientStrategy();
            case SPARK -> new SparkModelClientStrategy();
            case GLM -> new GlmModelClientStrategy();
            default -> throw new ServiceException("不支持的大模型厂商标识：" + modelInfo.getManufacturerType());
        };
    }

}
