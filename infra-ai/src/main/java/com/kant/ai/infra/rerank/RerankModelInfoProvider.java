package com.kant.ai.infra.rerank;

import java.util.List;

/**
 * Rerank 模型配置提供者。
 *
 * <p>infra-ai 只定义读取模型配置的抽象，不直接依赖 eval 或数据库 Mapper。
 * 如果后续新增 rerank_model_info 表，可以在业务模块实现该接口并覆盖默认 Provider。</p>
 */
public interface RerankModelInfoProvider {

    /**
     * 查询当前启用的 Rerank 模型配置。
     *
     * @return 启用的 Rerank 模型配置列表
     */
    List<RerankModelInfo> listEnabledRerankModels();
}
