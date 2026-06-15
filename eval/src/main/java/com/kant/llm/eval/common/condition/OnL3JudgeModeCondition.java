package com.kant.llm.eval.common.condition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * L3 Judge 模式条件判断器。
 *
 * <p>默认使用 real，便于当前阶段直接接入 JudgeConfiguration 中的裁判大模型。
 * 如需回到纯降级链路，可配置 app.l3.judge-mode=default。</p>
 */
@Slf4j
public class OnL3JudgeModeCondition implements Condition {

    private static final String ATTRIBUTE_VALUE = "value";

    private static final String PROPERTY_JUDGE_MODE = "app.l3.judge-mode";

    private static final String DEFAULT_JUDGE_MODE = "real";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnL3JudgeMode.class.getName());
        if (attributes == null) {
            return false;
        }
        String expectedMode = String.valueOf(attributes.get(ATTRIBUTE_VALUE));
        String actualMode = context.getEnvironment().getProperty(PROPERTY_JUDGE_MODE);
        if (!StringUtils.hasText(actualMode)) {
            actualMode = DEFAULT_JUDGE_MODE;
        }
        boolean matched = expectedMode.equalsIgnoreCase(actualMode);
        log.debug("L3 Judge 模式条件匹配，expectedMode: {}, actualMode: {}, matched: {}",
                expectedMode, actualMode, matched);
        return matched;
    }
}
