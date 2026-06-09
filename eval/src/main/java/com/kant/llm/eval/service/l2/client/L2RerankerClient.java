package com.kant.llm.eval.service.l2.client;

import com.kant.llm.eval.service.l2.model.L2RerankRequest;
import com.kant.llm.eval.service.l2.model.L2RerankResult;

import java.util.List;

/**
 * L2 Reranker 精排客户端。
 *
 * <p>用于抽象真实 Reranker 模型调用。第一阶段默认使用召回分降级为精排分。</p>
 */
public interface L2RerankerClient {

    /**
     * 对 RRF 候选进行精排。
     *
     * @param request 精排请求
     * @return 精排结果
     */
    List<L2RerankResult> rerank(L2RerankRequest request);
}
