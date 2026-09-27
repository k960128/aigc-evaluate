package com.kant.springai.agent.dto;

import java.math.BigDecimal;

public record SalesRepDTO(
        Long repId,
        String repName,
        Long regionId,
        String regionName,
        BigDecimal totalAmount,
        Integer orderCount
) {
}