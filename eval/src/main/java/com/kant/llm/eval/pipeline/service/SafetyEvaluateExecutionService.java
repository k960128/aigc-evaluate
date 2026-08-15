package com.kant.llm.eval.pipeline.service;

import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;

/**
 * 单样本安全评测执行服务。
 */
public interface SafetyEvaluateExecutionService {

    /**
     * 执行 L1/L2/L3 安全评测阶段。
     *
     * @param message 安全评测消息
     */
    void evaluate(SafetyEvaluateMessage message);
}
