package com.kant.llm.eval.common.condition;

import com.kant.llm.eval.common.config.L2RecallModeProperties;
import com.kant.llm.eval.common.config.L2RecallModeProperties.RecallMode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * L2 召回模式条件。
 *
 * <p>用于在 mysql-mock、empty、real 三种召回实现之间选择唯一 Bean，
 * 同时兼容历史 app.l2.mock-recall-enabled 开关。</p>
 */
@Slf4j
public class OnL2RecallModeCondition implements Condition {

    public static final String MODE_ATTRIBUTE = "mode";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnL2RecallMode.class.getName());
        if (attributes == null) {
            return false;
        }
        RecallMode expectedMode = (RecallMode) attributes.get(MODE_ATTRIBUTE);
        RecallMode actualMode = resolveMode(context);
        boolean matched = actualMode.equals(expectedMode);
        log.debug("L2 召回模式条件匹配，expectedMode: {}, actualMode: {}, matched: {}",
                expectedMode, actualMode, matched);
        return matched;
    }

    private RecallMode resolveMode(ConditionContext context) {
        String recallMode = context.getEnvironment().getProperty("app.l2.recall-mode");
        if (StringUtils.isNotBlank(recallMode)) {
            // 新配置优先：明确指定 mysql-mock / empty / real，避免历史开关和真实召回模式产生歧义。
            return L2RecallModeProperties.RecallMode.from(recallMode);
        }
        Boolean mockEnabled = context.getEnvironment().getProperty("app.l2.mock-recall-enabled", Boolean.class, Boolean.TRUE);
        return Boolean.FALSE.equals(mockEnabled) ? RecallMode.EMPTY : RecallMode.MYSQL_MOCK;
    }
}
