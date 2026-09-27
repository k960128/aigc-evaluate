package com.kant.springai.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesOrderDTO(
        Long id,
        String orderNo,
        Long repId,
        Long productId,
        Long regionId,
        String customerName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        BigDecimal cost,
        BigDecimal profit,
        String status,
        LocalDate orderDate
) {
}