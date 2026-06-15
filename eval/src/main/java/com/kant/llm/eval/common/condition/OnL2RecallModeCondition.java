package com.kant.llm.eval.common.condition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * L2 召回模式条件判断器。
 */
@Slf4j
public class OnL2RecallModeCondition implements Condition {

    private static final String ATTRIBUTE_VALUE = "value";

    private static final String PROPERTY_RECALL_MODE = "app.l2.recall-mode";

    private static final String DEFAULT_RECALL_MODE = "mysql-mock";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnL2RecallMode.class.getName());
        if (attributes == null) {
            return false;
        }
        String expectedMode = String.valueOf(attributes.get(ATTRIBUTE_VALUE));
        String actualMode = context.getEnvironment().getProperty(PROPERTY_RECALL_MODE);
        if (!StringUtils.hasText(actualMode)) {
            actualMode = DEFAULT_RECALL_MODE;
        }
        boolean matched = expectedMode.equalsIgnoreCase(actualMode);
        log.debug("L2 召回模式条件匹配，expectedMode: {}, actualMode: {}, matched: {}",
                expectedMode, actualMode, matched);
        return matched;
    }
}
