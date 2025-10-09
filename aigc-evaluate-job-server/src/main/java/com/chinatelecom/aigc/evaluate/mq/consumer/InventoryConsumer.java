package com.chinatelecom.aigc.evaluate.mq.consumer;

import com.chinatelecom.aigc.evaluate.dto.model.OrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InventoryConsumer {

    @KafkaListener(topics = "order-created", groupId = "ecommerce-group")
    public void consumeOrder(OrderDTO order) {
        log.info("✅ 收到订单: {}", order);

        // 模拟库存扣减逻辑
        try {
            deductInventory(order.getProductId(), order.getQuantity());
            log.info("📦 已扣减 {} 数量的库存: {}", order.getQuantity(), order.getProductId());
        } catch (Exception e) {
            log.error("❌ 扣减库存失败: ", e);
            // 可以考虑重试机制或发送到死信队列
        }
    }

    private void deductInventory(String productId, Integer quantity) {
        // 实际项目中调用库存服务或数据库
        System.out.printf("🔧 扣减商品 %s 的库存 %d 件\n", productId, quantity);
    }
}
