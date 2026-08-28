package com.kant.llm.eval.pipeline.service.ratelimit;

import com.kant.llm.eval.basesource.dto.resp.ModelInfoVO;

/**
 * 被评测模型调用并发限流器。
 *
 * <p>限流粒度是 model_info.id，许可数量来自 model_info.max_thread_size。</p>
 */
public interface ModelCallRateLimiter {

    /**
     * 获取模型调用许可。
     *
     * @param modelInfo 被评测模型配置
     * @return 可释放的调用许可
     */
    ModelCallPermit acquire(ModelInfoVO modelInfo);
}
