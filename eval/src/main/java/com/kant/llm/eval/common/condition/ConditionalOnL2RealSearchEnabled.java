package com.kant.llm.eval.common.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅在 L2 真实检索能力开启时注册 Bean。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnL2RealSearchEnabledCondition.class)
public @interface ConditionalOnL2RealSearchEnabled {
}
