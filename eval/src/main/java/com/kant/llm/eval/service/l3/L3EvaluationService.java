package com.kant.llm.eval.service.l3;

import com.kant.llm.eval.service.l3.model.L3EvaluationRequest;
import com.kant.llm.eval.service.l3.model.L3EvaluationResult;

/**
 * L3 Judge 裁判层服务。
 */
public interface L3EvaluationService {

    /**
     * 执行 L3 Judge 判定。
     *
     * @param request L3 判定请求
     * @return L3 判定结果
     */
    L3EvaluationResult evaluate(L3EvaluationRequest request);
}
