package com.kant.ai.infra.embedding;

import com.kant.ai.infra.enums.ModelManufacturerEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 模型注册表。
 *
 * <p>启动时或首次调用时加载启用模型配置，并构建本地 EmbeddingModel 客户端对象。
 * 这里不会主动发起远程 Embedding 请求，避免外部服务波动影响应用启动。</p>
 */
@Slf4j
public class EmbeddingModelRegistry {

    private static final EmbeddingModelInfo FALLBACK_MODEL_INFO = EmbeddingModelInfo.builder()
            .modelId(0L)
            .model("text-embedding-v4")
            .modelCode("text-embedding-v4")
            .manufacturerType(ModelManufacturerEnum.QWEN)
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
            .apiKey("sk-ws-*****************************************")
            .dimension(1024)
            .batchSize(10)
            .distanceMetric("COSINE")
            .normalize(false)
            .status(true)
            .build();

    private final EmbeddingModelStrategyFactory strategyFactory;

    private final ObjectProvider<EmbeddingModelInfoProvider> modelInfoProvider;

    private final Map<String, RegisteredEmbeddingModel> modelMap = new ConcurrentHashMap<>();

    private final Object initLock = new Object();

    private volatile boolean initialized;

    @Getter
    private volatile EmbeddingModelInfo defaultModelInfo;

    @Getter
    private volatile EmbeddingModel defaultEmbeddingModel;

    public EmbeddingModelRegistry(EmbeddingModelStrategyFactory strategyFactory,
                                  ObjectProvider<EmbeddingModelInfoProvider> modelInfoProvider) {
        this.strategyFactory = strategyFactory;
        this.modelInfoProvider = modelInfoProvider;
    }

    /**
     * 初始化启用的 Embedding 模型配置和本地客户端对象。
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        synchronized (initLock) {
            if (initialized) {
                return;
            }
            doInitialize();
            initialized = true;
        }
    }

    public EmbeddingModel getDefaultEmbeddingModel() {
        ensureInitialized();
        return defaultEmbeddingModel;
    }

    public EmbeddingModel getEmbeddingModel(String modelIdOrCode) {
        ensureInitialized();
        if (StringUtils.isBlank(modelIdOrCode)) {
            return defaultEmbeddingModel;
        }
        RegisteredEmbeddingModel registeredModel = modelMap.get(modelIdOrCode.trim());
        if (registeredModel == null) {
            log.warn("未找到指定 Embedding 模型，使用默认模型兜底，modelIdOrCode: {}", modelIdOrCode);
            return defaultEmbeddingModel;
        }
        return registeredModel.embeddingModel();
    }

    public int getDefaultDimension() {
        ensureInitialized();
        if (defaultModelInfo == null || defaultModelInfo.getDimension() == null) {
            return 0;
        }
        return defaultModelInfo.getDimension();
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private void doInitialize() {
        modelMap.clear();
        defaultModelInfo = null;
        defaultEmbeddingModel = null;

        List<EmbeddingModelInfo> modelInfos = loadEnabledModelInfos();
        if (modelInfos.isEmpty()) {
            modelInfos = List.of(FALLBACK_MODEL_INFO);
            log.warn("未加载到启用的 Embedding 模型配置，使用默认 Qwen text-embedding-v4 兜底。");
        }

        List<EmbeddingModelInfo> sortedModelInfos = new ArrayList<>(modelInfos);
        sortedModelInfos.sort(Comparator.comparing(EmbeddingModelInfo::getModelId,
                Comparator.nullsLast(Long::compareTo)));

        for (EmbeddingModelInfo modelInfo : sortedModelInfos) {
            registerModel(modelInfo);
        }

        if (defaultEmbeddingModel == null) {
            registerModel(FALLBACK_MODEL_INFO);
        }

        log.info("Embedding 模型注册表初始化完成，defaultModelId: {}, defaultModelCode: {}, dimension: {}",
                defaultModelInfo == null ? null : defaultModelInfo.getModelId(),
                defaultModelInfo == null ? null : defaultModelInfo.getModelCode(),
                defaultModelInfo == null ? null : defaultModelInfo.getDimension());
    }

    private List<EmbeddingModelInfo> loadEnabledModelInfos() {
        try {
            EmbeddingModelInfoProvider provider = modelInfoProvider.getIfAvailable();
            if (provider == null) {
                return List.of();
            }
            List<EmbeddingModelInfo> modelInfos = provider.listEnabledEmbeddingModels();
            return modelInfos == null ? List.of() : modelInfos;
        } catch (Exception ex) {
            log.warn("加载数据库 Embedding 模型配置失败，使用默认模型兜底，reason: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    private void registerModel(EmbeddingModelInfo modelInfo) {
        if (modelInfo == null || Boolean.FALSE.equals(modelInfo.getStatus())) {
            return;
        }
        try {
            EmbeddingModelStrategy strategy = strategyFactory.getStrategy(modelInfo);
            EmbeddingModel embeddingModel = strategy.buildEmbeddingModel(modelInfo);
            RegisteredEmbeddingModel registeredModel = new RegisteredEmbeddingModel(modelInfo, embeddingModel);
            registerKey(modelInfo.getModelId() == null ? null : String.valueOf(modelInfo.getModelId()), registeredModel);
            registerKey(modelInfo.getModelCode(), registeredModel);
            if (defaultEmbeddingModel == null) {
                defaultModelInfo = modelInfo;
                defaultEmbeddingModel = embeddingModel;
            }
            log.info("Embedding 模型已注册，modelId: {}, modelCode: {}, manufacturer: {}, model: {}, dimension: {}",
                    modelInfo.getModelId(), modelInfo.getModelCode(), modelInfo.getManufacturerType(),
                    modelInfo.getModel(), modelInfo.getDimension());
        } catch (Exception ex) {
            log.warn("注册 Embedding 模型失败，modelId: {}, modelCode: {}, reason: {}",
                    modelInfo.getModelId(), modelInfo.getModelCode(), ex.getMessage(), ex);
        }
    }

    private void registerKey(String key, RegisteredEmbeddingModel registeredModel) {
        if (StringUtils.isNotBlank(key)) {
            modelMap.put(key.trim(), registeredModel);
        }
    }

    private record RegisteredEmbeddingModel(EmbeddingModelInfo modelInfo, EmbeddingModel embeddingModel) {
    }
}
