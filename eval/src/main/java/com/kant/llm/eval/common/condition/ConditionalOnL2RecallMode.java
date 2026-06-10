package com.kant.llm.eval.common.condition;

import com.kant.llm.eval.common.config.L2RecallModeProperties.RecallMode;
import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按 L2 召回模式注册 Bean。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnL2RecallModeCondition.class)
public @interface ConditionalOnL2RecallMode {

    /** 需要匹配的召回模式。 */
    RecallMode mode();
}
