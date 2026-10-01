package com.kant.llm.eval.agent.validation.config;
import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
/** 固定验证独立线程池，不抢占探索Worker，数据库租约而非线程池承担恢复语义。 */
@Configuration
public class ValidationConfiguration {
    @Bean
    public TaskExecutor validationWorkerExecutor() {
        var executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(2); executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0); executor.setThreadNamePrefix("validation-worker-"); executor.initialize(); return executor;
    }
}
