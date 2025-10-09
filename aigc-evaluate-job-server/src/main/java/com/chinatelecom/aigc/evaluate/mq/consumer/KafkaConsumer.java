package com.chinatelecom.aigc.evaluate.mq.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {

    @KafkaListener(topics = "test_topic", groupId = "test_group")
    public void receiveMessage(String message) {
        System.out.println("Received message: " + message);
    }
}
