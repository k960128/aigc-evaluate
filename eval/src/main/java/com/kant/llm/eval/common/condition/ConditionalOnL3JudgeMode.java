package com.kant.llm.eval.common.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * L3 Judge 模式条件注解。
 *
 * <p>根据 app.l3.judge-mode 在 real、default 两种 L3JudgeClient 实现中选择一个。
 * real 表示调用裁判大模型，default 表示使用降级人工核验实现。</p>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OnL3JudgeModeCondition.class)
public @interface ConditionalOnL3JudgeMode {

    /**
     * 当前 Bean 期望的 L3 Judge 模式。
     */
    String value();
}
