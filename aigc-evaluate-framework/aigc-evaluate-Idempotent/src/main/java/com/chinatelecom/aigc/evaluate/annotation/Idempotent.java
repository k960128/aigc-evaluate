package com.chinatelecom.aigc.evaluate.annotation;

import com.chinatelecom.aigc.evaluate.enums.IdempotentTypeEnum;

import java.lang.annotation.*;

/**
 * 幂等注解
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    /**
     * 幂等Key，只有在 {@link Idempotent#type()} 为 {@link IdempotentTypeEnum#SPEL} 时生效
     */
    String key() default "";

    /**
     * 触发幂等失败逻辑时，返回的错误提示信息
     */
    String message() default "您操作太快，请稍后再试";

    /**
     * 验证幂等类型，支持多种幂等方式
     * RestAPI 优先使用{@link IdempotentTypeEnum#PARAM}
     * 其它类型幂等验证，使用 {@link IdempotentTypeEnum#SPEL}
     */
    IdempotentTypeEnum type() default IdempotentTypeEnum.PARAM;
}
