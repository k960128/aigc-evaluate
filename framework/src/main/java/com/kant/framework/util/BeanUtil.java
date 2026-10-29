package com.kant.framework.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class BeanUtil implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // Spring 启动时，会自动调用此方法，我们将上下文保存到静态变量
        BeanUtil.context = applicationContext;
    }

    // 提供一个静态方法，用于获取 Bean
    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化，BeanUtil 工具类可能未被 Spring 扫描到。");
        }
        return context.getBean(beanClass);
    }
}
