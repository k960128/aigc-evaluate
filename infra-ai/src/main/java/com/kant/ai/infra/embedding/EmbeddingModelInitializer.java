package com.kant.ai.infra.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Embedding 模型启动初始化器。
 *
 * <p>只初始化模型配置、策略映射和本地客户端对象，不主动请求远程 Embedding API。</p>
 */
@Slf4j
public class EmbeddingModelInitializer implements ApplicationRunner {

    private final EmbeddingModelRegistry embeddingModelRegistry;

    public EmbeddingModelInitializer(EmbeddingModelRegistry embeddingModelRegistry) {
        this.embeddingModelRegistry = embeddingModelRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            embeddingModelRegistry.initialize();
        } catch (Exception ex) {
            log.warn("Embedding 模型启动初始化失败，将在首次调用时继续使用兜底逻辑，reason: {}", ex.getMessage(), ex);
        }
    }
}
