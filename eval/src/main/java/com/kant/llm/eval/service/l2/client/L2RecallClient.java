package com.kant.llm.eval.service.l2.client;

import com.kant.llm.eval.service.l2.model.L2RecallRequest;
import com.kant.llm.eval.service.l2.model.L2RecallResult;

/**
 * L2 双路召回客户端。
 *
 * <p>用于抽象 ES 和 Milvus 的真实调用。第一阶段提供降级实现，后续替换为真实客户端即可。</p>
 */
public interface L2RecallClient {

    /**
     * 执行 ES/Milvus 双路召回。
     *
     * @param request 召回请求
     * @return 召回结果
     */
    L2RecallResult recall(L2RecallRequest request);
}
