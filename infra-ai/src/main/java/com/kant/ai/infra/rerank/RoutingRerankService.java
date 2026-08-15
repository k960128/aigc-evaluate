package com.kant.ai.infra.rerank;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Rerank 路由服务实现。
 *
 * <p>该类只负责参数校验和模型路由，不直接绑定任何具体厂商 SDK。
 * 具体厂商调用由 RerankModelRegistry 根据模型配置路由到对应策略。</p>
 */
@Slf4j
public class RoutingRerankService implements RerankService {

    private final RerankModelRegistry rerankModelRegistry;

    public RoutingRerankService(RerankModelRegistry rerankModelRegistry) {
        this.rerankModelRegistry = rerankModelRegistry;
    }

    @Override
    public RerankServiceResult rerank(String queryText, List<String> documents) {
        return rerank(null, queryText, documents);
    }

    @Override
    public RerankServiceResult rerank(String modelIdOrCode, String queryText, List<String> documents) {
        if (StringUtils.isBlank(queryText) || documents == null || documents.isEmpty()) {
            return RerankServiceResult.builder()
                    .degraded(true)
                    .reason("Rerank queryText 或 documents 为空")
                    .build();
        }
        try {
            return rerankModelRegistry.rerank(modelIdOrCode, queryText, documents);
        } catch (Exception ex) {
            log.warn("Rerank 服务调用失败，进入业务侧降级处理，modelIdOrCode: {}, reason: {}",
                    modelIdOrCode, ex.getMessage(), ex);
            return RerankServiceResult.builder()
                    .degraded(true)
                    .reason(ex.getMessage())
                    .build();
        }
    }
}
