package com.kant.llm.eval.common.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * L2 召回模式条件注解。
 *
 * <p>根据 app.l2.recall-mode 在 mysql-mock、real、empty 三种 L2RecallClient 实现中选择一个。</p>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnL2RecallModeCondition.class)
public @interface ConditionalOnL2RecallMode {

    /**
     * 当前 Bean 期望的召回模式。
     */
    String value();
}
