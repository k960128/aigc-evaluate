package com.kant.springai.agent.dto;

import java.math.BigDecimal;

public record SaProductDTO(
        Long productId,
        String skuCode,
        String productName,
        String category,
        BigDecimal totalAmount,
        Integer totalQuantity
) {
}