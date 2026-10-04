package com.kant.llm.eval.agent.production.config;

import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.mybatis.spring.annotation.MapperScan;

/** 独立有限生产线程池，避免与探索或固定回归争抢相同Worker槽位。 */
@Configuration
@MapperScan("com.kant.llm.eval.agent.production.dao.mapper")
public class SampleFactoryConfiguration {
    /**
     * 创建独立2线程、零等待队列的生产执行器。
     * 满载时不在内存无限堆积任务；Worker投递失败释放租约，后续从数据库重新领取。
     * 该Bean只提供执行线程，不负责开启Worker或每日触发器。
     */
    @Bean
    public TaskExecutor sampleProductionExecutor() {
        var e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(2);
        e.setQueueCapacity(0);
        e.setThreadNamePrefix("sample-production-");
        e.initialize();
        return e;
    }
}
