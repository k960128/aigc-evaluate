package com.kant.llm.eval.client;

import com.kant.llm.eval.client.strategy.*;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.service.ModelInfoService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端策略工厂
 */
@Slf4j
@Service
public class ModelClientStrategyFactory {

    private final ModelInfoService modelInfoService;

    private final Map<Long, ModelClientStrategy> strategyMap = new ConcurrentHashMap<>();

    public ModelClientStrategyFactory(ModelInfoService modelInfoService) {
        this.modelInfoService = modelInfoService;
    }

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
            case GPT -> new GptModelClientStrategy();
            default -> throw new ServiceException("不支持的大模型厂商标识：" + modelInfo.getManufacturerType());
        };
    }

    @Lazy
    @PostConstruct
    public void init() {
        modelInfoService.list().forEach(modelInfoDO -> {
            ModelInfo modelInfo = new ModelInfo();
            modelInfo.setModelId(modelInfoDO.getId());
            modelInfo.setManufacturerType(ModelManufacturerEnum.valueOf(modelInfoDO.getManufacturerCode()));
            modelInfo.setModel(modelInfoDO.getModel());
            modelInfo.setApiKey(modelInfoDO.getApiKey());
            modelInfo.setBaseUrl(modelInfoDO.getBaseUrl());
            createStrategy(modelInfo);
        });
        log.info("模型客户端策略工厂初始化完成");
    }

}
