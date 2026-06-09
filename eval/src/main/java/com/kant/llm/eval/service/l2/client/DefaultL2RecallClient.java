package com.kant.llm.eval.service.l2.client;

import com.kant.llm.eval.service.l2.model.L2RecallRequest;
import com.kant.llm.eval.service.l2.model.L2RecallResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L2 召回降级客户端。
 *
 * <p>当前不强依赖真实 ES/Milvus 配置，因此默认返回空召回结果，
 * 让主评测链路可以先完成 L2 节点闭环。</p>
 */
@Slf4j
@Component
public class DefaultL2RecallClient implements L2RecallClient {

    @Override
    public L2RecallResult recall(L2RecallRequest request) {
        log.info("L2 召回客户端处于降级模式，返回空 ES/Milvus 命中，esTopK: {}, milvusTopK: {}",
                request.getEsTopK(), request.getMilvusTopK());
        return L2RecallResult.builder()
                .esHits(List.of())
                .milvusHits(List.of())
                .degraded(true)
                .build();
    }
}
