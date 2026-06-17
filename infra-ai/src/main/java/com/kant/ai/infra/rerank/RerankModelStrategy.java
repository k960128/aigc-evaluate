package com.kant.ai.infra.rerank;

import com.kant.ai.infra.enums.ModelManufacturerEnum;

import java.util.List;

/**
 * Rerank 模型厂商策略接口。
 *
 * <p>不同厂商的 SDK 初始化、请求参数和响应结构可能不同，这些差异统一收敛到策略实现中。
 * 上层 RerankService 只负责路由，不直接关心底层 SDK 细节。</p>
 */
public interface RerankModelStrategy {

    /**
     * 根据模型配置构建本地 Rerank 客户端对象。
     *
     * <p>该方法只创建本地对象，不应该主动调用远程模型服务。</p>
     */
    Object buildRerankModel(RerankModelInfo modelInfo);

    /**
     * 调用具体厂商的 Rerank 模型。
     *
     * @param modelInfo   当前模型配置
     * @param rerankModel buildRerankModel 构建出来的本地客户端对象
     * @param queryText   查询文本
     * @param documents   候选文本列表
     * @return 统一的 Rerank 调用结果
     */
    RerankServiceResult rerank(RerankModelInfo modelInfo, Object rerankModel, String queryText, List<String> documents);

    /**
     * 当前策略支持的模型厂商。
     */
    ModelManufacturerEnum getManufacturer();
}
