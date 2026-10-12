package com.kant.llm.eval.agent.production.config;

import com.kant.agent.service.production.SampleReflectionAgent;
import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.mybatis.spring.annotation.MapperScan;

/** 独立有限生产线程池，避免与探索或固定回归争抢相同Worker槽位。 */
@Configuration
@MapperScan("com.kant.llm.eval.agent.production.dao.mapper")
public class SampleFactoryConfiguration {
    /**
     * D05配置只装配样本工厂自己的Agent和执行线程，不与D01-D04共享后台线程池。
     * 这样可以控制生产模型调用的并发度，也避免一个模块的阻塞拖垮其他评测业务。
     */
    /** 无状态Reflection决策核心，供执行器与事务结算共享；不在Bean创建时调用模型。 */
    @Bean
    public SampleReflectionAgent sampleReflectionAgent() {
        // Agent本身无状态，不在Bean初始化阶段读取数据库或调用模型。
        return new SampleReflectionAgent();
    }
    /**
     * 创建独立2线程、零等待队列的生产执行器。
     * 满载时不在内存无限堆积任务；Worker投递失败释放租约，后续从数据库重新领取。
     * 该Bean只提供执行线程，不负责开启Worker或每日触发器。
     */
    @Bean
    public TaskExecutor sampleProductionExecutor() {
        // 零队列意味着线程池满载时由数据库作为等待队列，避免任务无限堆积在JVM内存。
        var e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(2);
        e.setQueueCapacity(0);
        e.setThreadNamePrefix("sample-production-");
        e.initialize();
        return e;
    }
}
