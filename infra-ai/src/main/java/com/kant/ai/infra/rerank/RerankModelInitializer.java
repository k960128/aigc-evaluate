package com.kant.ai.infra.rerank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Rerank 模型启动初始化器。
 *
 * <p>只初始化模型配置、策略映射和本地客户端对象，不主动请求远程 Rerank API。
 * 如果初始化失败，后续首次调用仍会触发注册表的兜底初始化。</p>
 */
@Slf4j
public class RerankModelInitializer implements ApplicationRunner {

    private final RerankModelRegistry rerankModelRegistry;

    public RerankModelInitializer(RerankModelRegistry rerankModelRegistry) {
        this.rerankModelRegistry = rerankModelRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            rerankModelRegistry.initialize();
        } catch (Exception ex) {
            log.warn("Rerank 模型启动初始化失败，将在首次调用时继续使用兜底逻辑，reason: {}", ex.getMessage(), ex);
        }
    }
}
