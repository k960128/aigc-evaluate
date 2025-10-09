package com.chinatelecom.aigc.evaluate.mq.consumer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutorConsumer {
    private final KafkaListenerEndpointRegistry registry;
    private final ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory;
    private final ConsumerFactory<String, String> consumerFactory;

    public TaskExecutorConsumer(KafkaListenerEndpointRegistry registry,
                                ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory,
                                ConsumerFactory<String, String> consumerFactory) {
        this.registry = registry;
        this.kafkaListenerContainerFactory = kafkaListenerContainerFactory;
        this.consumerFactory = consumerFactory;
    }
}
