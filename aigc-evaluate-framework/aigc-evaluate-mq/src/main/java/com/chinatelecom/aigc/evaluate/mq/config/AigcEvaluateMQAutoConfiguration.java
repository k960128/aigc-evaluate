package com.chinatelecom.aigc.evaluate.mq.config;

import com.chinatelecom.aigc.evaluate.mq.execute.ExecutorQueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AigcEvaluateMQAutoConfiguration {

    /**
     * 从配置文件中读取队列类型，默认使用 blocking（阻塞队列）
     * 可选值：blocking / local
     */
    @Value("${aigc.mq.queue-type:blocking}")
    private String queueType;

    @Bean
    public ExecutorQueueService executorQueueService() {
        return new ExecutorQueueService(queueType);
    }
}
