package com.kant.llm.eval.pipeline.service;

import com.kant.llm.eval.pipeline.mq.message.ModelCallMessage;

/**
 * 单样本模型调用执行服务。
 */
public interface ModelCallExecutionService {

    /**
     * 执行模型调用阶段。
     *
     * @param message 模型调用消息
     */
    void execute(ModelCallMessage message);
}
