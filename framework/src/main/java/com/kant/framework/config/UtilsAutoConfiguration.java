package com.kant.framework.config;

import com.kant.framework.util.BeanUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class UtilsAutoConfiguration {
    @Bean
    public BeanUtil beanUtil() {
        return new BeanUtil();
    }
}
