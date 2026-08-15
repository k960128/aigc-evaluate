package com.kant.llm.examples.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 模拟订单管理服务
 */
@Slf4j
@Service
public class OrderManagerService {

    /**
     * 订单退款
      * @param orderId 订单ID
     * @param reason 退款原因
     * @return 退款订单ID
     */
    public String refund(String orderId, String reason) {
        log.info("退款成功，订单ID：{}，退款原因：{}", orderId, reason);
        return UUID.randomUUID().toString();
    }
}
