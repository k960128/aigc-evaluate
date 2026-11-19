package com.kant.agent.config;

import com.kant.agent.simple.metrics.BaselineRepository;
import com.kant.agent.simple.metrics.InMemoryBaselineRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 简单的自动配置类，用于配置 BaselineRepository。
 */
@AutoConfiguration
public class SimpleAutoConfiguration {

    @Bean(name = "baselineRepository")
    public BaselineRepository baselineRepository() {
        return new InMemoryBaselineRepository();
    }
}
