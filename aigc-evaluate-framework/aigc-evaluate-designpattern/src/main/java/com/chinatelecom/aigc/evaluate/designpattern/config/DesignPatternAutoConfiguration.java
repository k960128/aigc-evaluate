package com.chinatelecom.aigc.evaluate.designpattern.config;

import com.chinatelecom.aigc.evaluate.designpattern.chain.AbstractChainContext;
import com.chinatelecom.aigc.evaluate.designpattern.strategy.AbstractStrategyChoose;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 策略模式自动配置
 * @author AIGC
 */
@AutoConfiguration
public class DesignPatternAutoConfiguration {


    /**
     * 策略模式返回器Bean
     */
    @Bean
    public AbstractStrategyChoose abstractStrategyChoose() {
        return new AbstractStrategyChoose();
    }

    /**
     * 责任链上下文Bean
     */
    @Bean
    public AbstractChainContext abstractChainContext() {
        return new AbstractChainContext();
    }
}
