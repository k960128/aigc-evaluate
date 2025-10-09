package com.chinatelecom.aigc.evaluate.controller;

import com.chinatelecom.aigc.evaluate.dto.model.OrderDTO;
import com.chinatelecom.aigc.evaluate.dto.req.CreateOrderRequest;
import com.chinatelecom.aigc.evaluate.mq.producer.OrderProducer;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/aigc/evaluate/msg")
public class MsgController {


    private final OrderProducer orderProducer;

    public MsgController(OrderProducer orderProducer) {
        this.orderProducer = orderProducer;
    }

    @PostMapping("/createOrder")
    public String createOrder(@RequestBody CreateOrderRequest request) {
        String orderId = "ORD-" + System.currentTimeMillis();
        OrderDTO order = new OrderDTO(
                orderId,
                request.getUserId(),
                request.getProductId(),
                request.getQuantity(),
                new BigDecimal(request.getAmount()),
                LocalDateTime.now()
        );

        orderProducer.sendOrderCreated(order);
        return "订单已创建并发送: " + orderId;
    }
}
