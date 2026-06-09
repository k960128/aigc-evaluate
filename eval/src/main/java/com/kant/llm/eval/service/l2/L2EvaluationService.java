package com.kant.llm.eval.service.l2;

import com.kant.llm.eval.service.l2.model.L2EvaluationRequest;
import com.kant.llm.eval.service.l2.model.L2EvaluationResult;

/**
 * L2 安全判定服务。
 */
public interface L2EvaluationService {

    /**
     * 执行 L2 ES/Milvus 双路召回、RRF 融合、Reranker 精排和阈值路由。
     *
     * @param request L2 判定请求
     * @return L2 判定结果
     */
    L2EvaluationResult evaluate(L2EvaluationRequest request);
}
