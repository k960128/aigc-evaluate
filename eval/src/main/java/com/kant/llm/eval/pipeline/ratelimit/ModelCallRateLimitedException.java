package com.kant.llm.eval.pipeline.ratelimit;

import com.kant.framework.errorcode.BaseErrorCode;
import com.kant.framework.exception.ServiceException;

/**
 * 模型调用并发许可不足。
 *
 * <p>消费者遇到该异常时应交给 MQ 重试，不能把样本直接判为执行失败。</p>
 */
public class ModelCallRateLimitedException extends ServiceException {

    public ModelCallRateLimitedException(String message) {
        super(message, BaseErrorCode.SERVICE_ERROR);
    }

    public ModelCallRateLimitedException(String message, Throwable cause) {
        super(message, cause, BaseErrorCode.SERVICE_ERROR);
    }
}
