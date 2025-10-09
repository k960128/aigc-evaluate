package com.chinatelecom.aigc.evaluate.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private String orderId;
    private String userId;
    private String productId;
    private Integer quantity;
    private BigDecimal amount;
    private LocalDateTime createTime;
}
