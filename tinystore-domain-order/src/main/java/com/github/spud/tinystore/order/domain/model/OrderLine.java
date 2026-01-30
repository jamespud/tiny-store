package com.github.spud.tinystore.order.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * OrderLine - 订单行（值对象）
 */
@Getter
@Builder
public class OrderLine {
    
    private Long id;
    private String orderId;
    
    private String skuId;
    private String productId;
    private String productName;
    
    private Integer quantity;
    private Long priceCents;
    private Long lineAmountCents;
}
