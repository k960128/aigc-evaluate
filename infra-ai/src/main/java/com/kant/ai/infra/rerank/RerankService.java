package com.kant.ai.infra.rerank;

import java.util.List;

/**
 * 通用 Rerank 服务。
 *
 * <p>infra-ai 只提供模型基础能力，不包含 L2 候选特征、阈值路由等评测业务语义。
 * 业务模块可以使用默认模型，也可以按 modelId/modelCode 显式路由到指定 Rerank 模型。</p>
 */
public interface RerankService {

    /**
     * 使用默认 Rerank 模型对候选文本进行重排。
     *
     * @param queryText 当前查询文本
     * @param documents 候选文本列表
     * @return Rerank 原始响应包装结果
     */
    RerankServiceResult rerank(String queryText, List<String> documents);

    /**
     * 使用指定 Rerank 模型对候选文本进行重排。
     *
     * @param modelIdOrCode 模型 ID 或模型编码
     * @param queryText     当前查询文本
     * @param documents     候选文本列表
     * @return Rerank 原始响应包装结果
     */
    RerankServiceResult rerank(String modelIdOrCode, String queryText, List<String> documents);
}
