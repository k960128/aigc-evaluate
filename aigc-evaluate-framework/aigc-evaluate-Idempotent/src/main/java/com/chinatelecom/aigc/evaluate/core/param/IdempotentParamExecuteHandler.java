package com.chinatelecom.aigc.evaluate.core.param;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.chinatelecom.aigc.evaluate.common.exception.ErrorCode;
import com.chinatelecom.aigc.evaluate.core.AbstractIdempotentExecuteHandler;
import com.chinatelecom.aigc.evaluate.core.IdempotentContext;
import com.chinatelecom.aigc.evaluate.core.IdempotentParamWrapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 基于方法参数验证请求幂等性
 * @author AIGC
 */
@RequiredArgsConstructor
public final class IdempotentParamExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentParamService {
    private static final Map<String, Boolean> cache = new ConcurrentHashMap<>();
    private final static String LOCK = "lock:param:restAPI";
    private static final ThreadLocal<String> LOCKNAME = new ThreadLocal<>();

    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        String lockKey = String.format("idempotent:md5:%s", calcArgsMD5(joinPoint));
        return IdempotentParamWrapper.builder().lockKey(lockKey).joinPoint(joinPoint).build();
    }


    /**
     * @return joinPoint md5
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        return DigestUtil.md5Hex(JSON.toJSONBytes(joinPoint.getArgs()));
    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        String lockKey = wrapper.getLockKey();
        LOCKNAME.set(lockKey);
        if (cache.containsKey(lockKey)) {
            throw exception(new ErrorCode(9999999, wrapper.getIdempotent().message()));
        }
        Boolean lock = true;
        cache.put(lockKey, lock);
        IdempotentContext.put(LOCK, lock);
    }

    @Override
    public void postProcessing() {
        Boolean lock = null;
        try {
            lock = (Boolean) IdempotentContext.getKey(LOCK);
        } finally {
            if (lock != null) {
                cache.remove(LOCKNAME.get());
            }
        }
    }

    @Override
    public void exceptionProcessing() {
        Boolean lock = null;
        try {
            lock = (Boolean) IdempotentContext.getKey(LOCK);
        } finally {
            if (lock != null) {
                cache.remove(LOCKNAME.get());
            }
        }
    }
}
