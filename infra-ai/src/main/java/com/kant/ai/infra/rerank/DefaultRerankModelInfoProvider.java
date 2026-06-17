package com.kant.ai.infra.rerank;

import java.util.List;

/**
 * 默认 Rerank 模型配置提供者。
 *
 * <p>当前项目暂未提供独立的 Rerank 模型配置表，因此先返回一个稳定的
 * DashScope/Qwen qwen3-rerank 配置，保证 infra-ai 自动配置后即可使用。</p>
 */
public class DefaultRerankModelInfoProvider implements RerankModelInfoProvider {

    @Override
    public List<RerankModelInfo> listEnabledRerankModels() {
        return List.of(RerankModelDefaults.fallbackModelInfo());
    }
}
