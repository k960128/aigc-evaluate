package com.kant.llm.eval.pipeline.service.ratelimit;

/**
 * 模型调用并发许可。
 *
 * <p>业务代码不直接感知 Redisson permitId，只需要在模型调用结束后释放许可。</p>
 */
public interface ModelCallPermit extends AutoCloseable {

    /** 释放本次模型调用占用的并发许可。 */
    void release();

    @Override
    default void close() {
        release();
    }
}
