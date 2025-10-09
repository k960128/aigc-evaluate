package com.chinatelecom.aigc.evaluate.mq.producer;

import com.chinatelecom.aigc.evaluate.dto.model.OrderDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducer {

    @Value("${kafka.topic.order-created:order-created}")
    private String topicName;

    private final KafkaTemplate<String, OrderDTO> kafkaTemplate;

    public OrderProducer(KafkaTemplate<String, OrderDTO> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrderCreated(OrderDTO order) {
        System.out.println("📤 发送订单消息: " + order);
        kafkaTemplate.send(topicName, order.getOrderId(), order);
    }
}
