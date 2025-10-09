package com.chinatelecom.aigc.evaluate.dto.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    private String userId;
    private String productId;
    private Integer quantity;
    private String amount;
}
