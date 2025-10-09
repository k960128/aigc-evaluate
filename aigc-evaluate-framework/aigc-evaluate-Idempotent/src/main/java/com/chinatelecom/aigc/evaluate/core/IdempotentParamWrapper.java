package com.chinatelecom.aigc.evaluate.core;

import com.chinatelecom.aigc.evaluate.annotation.Idempotent;
import com.chinatelecom.aigc.evaluate.enums.IdempotentTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 幂等参数包装
 * @author AIGC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class IdempotentParamWrapper {
    /**
     * 幂等注解
     */
    private Idempotent idempotent;

    /**
     * AOP 处理连接点
     */
    private ProceedingJoinPoint joinPoint;

    /**
     * 锁标识，{@link IdempotentTypeEnum#PARAM}
     */
    private String lockKey;
}
