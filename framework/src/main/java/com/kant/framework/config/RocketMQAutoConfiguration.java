package com.kant.framework.config;

import com.kant.framework.mq.producer.DelegatingTransactionListener;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.RocketMQProducerAdapter;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 消息队列自动装配配置
 * 默认启用，可通过 rocketmq.enable=false 关闭。
 */
@Configuration
@ConditionalOnProperty(prefix = "rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
public class RocketMQAutoConfiguration {

    @Bean
    public DelegatingTransactionListener delegatingTransactionListener() {
        return new DelegatingTransactionListener();
    }

    @Bean
    public MessageQueueProducer messageQueueProducer(RocketMQTemplate rocketMQTemplate,
                                                     DelegatingTransactionListener transactionListener) {
        return new RocketMQProducerAdapter(rocketMQTemplate, transactionListener);
    }
}
