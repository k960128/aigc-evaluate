package com.kant.springai.agent.dto;

import java.math.BigDecimal;

public record SalesRegionDTO(
        Long regionId,
        String regionName,
        BigDecimal totalAmount,
        Integer orderCount,
        BigDecimal totalProfit
) {
}