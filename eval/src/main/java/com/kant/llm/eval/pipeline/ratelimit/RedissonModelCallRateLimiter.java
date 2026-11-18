package com.kant.llm.eval.pipeline.ratelimit;

import com.kant.llm.eval.basesource.dto.resp.ModelInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的模型调用分布式信号量。
 *
 * <p>限流粒度是 model_info.id，许可数量来自 model_info.max_thread_size。
 * 这里使用 RPermitExpirableSemaphore 而不是普通信号量，是为了给每个许可设置租约，
 * 避免进程异常退出后许可永久泄漏。</p>
 */
@Slf4j
@Component
public class RedissonModelCallRateLimiter implements ModelCallRateLimiter {

    private static final String MODEL_CALL_SEMAPHORE_KEY_PREFIX = "aigc-eval:model-call:semaphore:";
    private static final int DEFAULT_MAX_CONCURRENCY = 1;
    private static final long WAIT_SECONDS = 3L;
    private static final long LEASE_MINUTES = 10L;

    private final RedissonClient redissonClient;

    public RedissonModelCallRateLimiter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public ModelCallPermit acquire(ModelInfoVO modelInfo) {
        if (modelInfo == null || modelInfo.getId() == null) {
            throw new ModelCallRateLimitedException("模型配置为空，无法获取模型调用并发许可");
        }
        int permits = resolvePermits(modelInfo.getMaxThreadSize());
        String semaphoreKey = MODEL_CALL_SEMAPHORE_KEY_PREFIX + modelInfo.getId();
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(semaphoreKey);
        // trySetPermits 只会在信号量首次创建时生效，避免每次调用都重置正在使用中的许可数量。
        semaphore.trySetPermits(permits);
        try {
            // 等待时间较短是为了把背压交还给 MQ，而不是长期占用消费线程。
            String permitId = semaphore.tryAcquire(WAIT_SECONDS, LEASE_MINUTES, TimeUnit.MINUTES);
            if (permitId == null) {
                throw new ModelCallRateLimitedException("模型调用并发许可不足，等待 MQ 重试，modelId="
                        + modelInfo.getId() + ", maxThreadSize=" + permits);
            }
            log.info("模型调用并发许可获取成功，modelId: {}, semaphoreKey: {}, permitId: {}, maxThreadSize: {}",
                    modelInfo.getId(), semaphoreKey, permitId, permits);
            return new RedissonModelCallPermit(semaphore, permitId, modelInfo.getId(), semaphoreKey);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModelCallRateLimitedException("模型调用并发许可获取被中断，modelId=" + modelInfo.getId(), ex);
        }
    }

    private int resolvePermits(Long maxThreadSize) {
        if (maxThreadSize == null || maxThreadSize <= 0) {
            return DEFAULT_MAX_CONCURRENCY;
        }
        return maxThreadSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : maxThreadSize.intValue();
    }

    private record RedissonModelCallPermit(RPermitExpirableSemaphore semaphore,
                                           String permitId,
                                           Long modelId,
                                           String semaphoreKey) implements ModelCallPermit {

        @Override
        public void release() {
            try {
                semaphore.release(permitId);
                log.info("模型调用并发许可已释放，modelId: {}, semaphoreKey: {}, permitId: {}",
                        modelId, semaphoreKey, permitId);
            } catch (Exception ex) {
                log.warn("模型调用并发许可释放失败，modelId: {}, semaphoreKey: {}, permitId: {}, reason: {}",
                        modelId, semaphoreKey, permitId, ex.getMessage(), ex);
            }
        }
    }
}
