package com.chinatelecom.aigc.evaluate.config;

import com.chinatelecom.aigc.evaluate.core.IdempotentAspect;
import com.chinatelecom.aigc.evaluate.core.param.IdempotentParamExecuteHandler;
import com.chinatelecom.aigc.evaluate.core.param.IdempotentParamService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 幂等组件自动装配
 * @author AIGC
 */
@EnableConfigurationProperties(IdempotentProperties.class)
public class IdempotentAutoConfiguration {

    @Bean
    public IdempotentAspect idempotentAspect() {
        return new IdempotentAspect();
    }

    /**
     * 参数方式幂等实现，基于 RestAPI 场景
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentParamService idempotentParamExecuteHandler() {
        return new IdempotentParamExecuteHandler();
    }

}
