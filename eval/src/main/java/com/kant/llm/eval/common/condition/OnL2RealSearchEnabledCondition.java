package com.kant.llm.eval.common.condition;

import com.kant.llm.eval.common.config.L2RecallModeProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * L2 真实检索能力启用条件。
 *
 * <p>真实召回或索引同步任一开启时，才注册 ES 相关 Bean。</p>
 */
@Slf4j
public class OnL2RealSearchEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String recallMode = context.getEnvironment().getProperty("app.l2.recall-mode");
        boolean realRecall = StringUtils.isNotBlank(recallMode)
                && L2RecallModeProperties.RecallMode.REAL.equals(L2RecallModeProperties.RecallMode.from(recallMode));
        boolean syncEnabled = context.getEnvironment().getProperty("app.l2.index-sync.enabled", Boolean.class, Boolean.FALSE);
        boolean enabled = realRecall || syncEnabled;
        log.debug("L2 真实检索条件匹配，recallMode: {}, realRecall: {}, indexSyncEnabled: {}, enabled: {}",
                recallMode, realRecall, syncEnabled, enabled);
        return enabled;
    }
}
