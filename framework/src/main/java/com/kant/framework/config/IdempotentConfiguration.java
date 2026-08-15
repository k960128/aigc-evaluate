package com.kant.framework.config;

import com.kant.framework.idempotent.IdempotentConsumeAspect;
import com.kant.framework.idempotent.IdempotentSubmitAspect;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

public class IdempotentConfiguration {

    @Bean
    public IdempotentSubmitAspect idempotentSubmitAspect(RedissonClient redissonClient) {
        return new IdempotentSubmitAspect(redissonClient);
    }

    @Bean
    public IdempotentConsumeAspect idempotentConsumeAspect(StringRedisTemplate stringRedisTemplate) {
        return new IdempotentConsumeAspect(stringRedisTemplate);
    }
}
