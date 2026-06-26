package com.kant.llm.examples.tools;

import com.kant.llm.examples.service.OrderManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 订单工具类
 */
@Slf4j
@Component
public class OrderTools {

    private final OrderManagerService orderManagerService;

    public OrderTools(OrderManagerService orderManagerService) {
        this.orderManagerService = orderManagerService;
    }

    @Tool(name = "apply_refund", description = "根据用户传入的订单信息发起退款")
    public String refund(@ToolParam(description = "订单编号，为数字类型") String orderId,
                         @ToolParam(description = "商品名称") String name,
                         @ToolParam(description = "退款原因") String reason) {
        log.info("已为商品:{}，订单号:{}申请退款 , 退款原因：{}", name, orderId, reason);
        orderManagerService.refund(orderId, reason);
        return "已为商品：" + name + ",订单号：" + orderId + "申请退款 , 退款原因： " + reason;
    }
}
