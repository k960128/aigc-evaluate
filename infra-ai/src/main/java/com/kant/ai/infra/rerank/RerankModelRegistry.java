package com.kant.ai.infra.rerank;

import com.kant.framework.exception.ServiceException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rerank 模型注册表。
 *
 * <p>启动时或首次调用时加载启用的模型配置，并构建本地 Rerank 客户端对象。
 * 注册表不会主动请求远程 Rerank API，因此不会因为外部模型服务波动而阻断应用启动。</p>
 */
@Slf4j
public class RerankModelRegistry {

    private final RerankModelStrategyFactory strategyFactory;

    private final ObjectProvider<RerankModelInfoProvider> modelInfoProvider;

    private final Map<String, RegisteredRerankModel> modelMap = new ConcurrentHashMap<>();

    private final Object initLock = new Object();

    private volatile boolean initialized;

    @Getter
    private volatile RerankModelInfo defaultModelInfo;

    private volatile Object defaultRerankModel;

    public RerankModelRegistry(RerankModelStrategyFactory strategyFactory,
                               ObjectProvider<RerankModelInfoProvider> modelInfoProvider) {
        this.strategyFactory = strategyFactory;
        this.modelInfoProvider = modelInfoProvider;
    }

    /**
     * 初始化启用的 Rerank 模型配置和本地客户端对象。
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

    /**
     * 使用默认模型执行 Rerank。
     */
    public RerankServiceResult rerank(String queryText, List<String> documents) {
        return rerank(null, queryText, documents);
    }

    /**
     * 使用指定模型执行 Rerank；找不到指定模型时自动回退到默认模型。
     */
    public RerankServiceResult rerank(String modelIdOrCode, String queryText, List<String> documents) {
        ensureInitialized();
        RegisteredRerankModel registeredModel = selectModel(modelIdOrCode);
        return registeredModel.strategy().rerank(registeredModel.modelInfo(), registeredModel.rerankModel(), queryText, documents);
    }

    /**
     * 获取默认 Rerank 本地客户端对象。
     *
     * <p>注册表保持厂商无关，只返回 Object；需要兼容具体 SDK 类型的地方由配置层自行转换。</p>
     */
    public Object getDefaultRerankModel() {
        ensureInitialized();
        return defaultRerankModel;
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private void doInitialize() {
        modelMap.clear();
        defaultModelInfo = null;
        defaultRerankModel = null;

        List<RerankModelInfo> modelInfos = loadEnabledModelInfos();
        if (modelInfos.isEmpty()) {
            modelInfos = List.of(RerankModelDefaults.fallbackModelInfo());
            log.warn("未加载到启用的 Rerank 模型配置，使用默认 Qwen qwen3-rerank 兜底。");
        }

        List<RerankModelInfo> sortedModelInfos = new ArrayList<>(modelInfos);
        sortedModelInfos.sort(Comparator.comparing(RerankModelInfo::getModelId,
                Comparator.nullsLast(Long::compareTo)));

        for (RerankModelInfo modelInfo : sortedModelInfos) {
            registerModel(modelInfo);
        }

        if (defaultRerankModel == null) {
            registerModel(RerankModelDefaults.fallbackModelInfo());
        }

        log.info("Rerank 模型注册表初始化完成，defaultModelId: {}, defaultModelCode: {}, manufacturer: {}, model: {}",
                defaultModelInfo == null ? null : defaultModelInfo.getModelId(),
                defaultModelInfo == null ? null : defaultModelInfo.getModelCode(),
                defaultModelInfo == null ? null : defaultModelInfo.getManufacturerType(),
                defaultModelInfo == null ? null : defaultModelInfo.getModel());
    }

    private List<RerankModelInfo> loadEnabledModelInfos() {
        try {
            RerankModelInfoProvider provider = modelInfoProvider.getIfAvailable();
            if (provider == null) {
                return List.of();
            }
            List<RerankModelInfo> modelInfos = provider.listEnabledRerankModels();
            return modelInfos == null ? List.of() : modelInfos;
        } catch (Exception ex) {
            log.warn("加载 Rerank 模型配置失败，使用默认模型兜底，reason: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    private void registerModel(RerankModelInfo modelInfo) {
        if (modelInfo == null || Boolean.FALSE.equals(modelInfo.getStatus())) {
            return;
        }
        try {
            RerankModelStrategy strategy = strategyFactory.getStrategy(modelInfo);
            Object rerankModel = strategy.buildRerankModel(modelInfo);
            RegisteredRerankModel registeredModel = new RegisteredRerankModel(modelInfo, rerankModel, strategy);
            registerKey(modelInfo.getModelId() == null ? null : String.valueOf(modelInfo.getModelId()), registeredModel);
            registerKey(modelInfo.getModelCode(), registeredModel);
            registerKey(modelInfo.getModel(), registeredModel);
            if (defaultRerankModel == null) {
                defaultModelInfo = modelInfo;
                defaultRerankModel = rerankModel;
            }
            log.info("Rerank 模型已注册，modelId: {}, modelCode: {}, manufacturer: {}, model: {}, topN: {}",
                    modelInfo.getModelId(), modelInfo.getModelCode(), modelInfo.getManufacturerType(),
                    modelInfo.getModel(), modelInfo.getTopN());
        } catch (Exception ex) {
            log.warn("注册 Rerank 模型失败，modelId: {}, modelCode: {}, reason: {}",
                    modelInfo.getModelId(), modelInfo.getModelCode(), ex.getMessage(), ex);
        }
    }

    private RegisteredRerankModel selectModel(String modelIdOrCode) {
        if (StringUtils.isBlank(modelIdOrCode)) {
            return defaultRegisteredModel();
        }
        RegisteredRerankModel registeredModel = modelMap.get(modelIdOrCode.trim());
        if (registeredModel == null) {
            log.warn("未找到指定 Rerank 模型，使用默认模型兜底，modelIdOrCode: {}", modelIdOrCode);
            return defaultRegisteredModel();
        }
        return registeredModel;
    }

    private RegisteredRerankModel defaultRegisteredModel() {
        if (defaultModelInfo == null || defaultRerankModel == null) {
            throw new ServiceException("默认 Rerank 模型未初始化");
        }
        RerankModelStrategy strategy = strategyFactory.getStrategy(defaultModelInfo);
        return new RegisteredRerankModel(defaultModelInfo, defaultRerankModel, strategy);
    }

    private void registerKey(String key, RegisteredRerankModel registeredModel) {
        if (StringUtils.isNotBlank(key)) {
            modelMap.put(key.trim(), registeredModel);
        }
    }

    private record RegisteredRerankModel(RerankModelInfo modelInfo, Object rerankModel, RerankModelStrategy strategy) {
    }
}
