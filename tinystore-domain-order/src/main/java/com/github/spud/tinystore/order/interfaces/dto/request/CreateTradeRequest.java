package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 创建交易请求
 */
@Data
public class CreateTradeRequest {
    
    private String tradeId;
    private String buyerId;
    private String buyerNick;
    private String addressId;
    private String couponCode;
    private String traceId;
    private List<OrderLineItem> orderLines;
    
    @Data
    public static class OrderLineItem {
        private String skuId;
        private String productId;
        private String productName;
        private String shopId;
        private String sellerId;
        private Integer quantity;
        private Long priceCents;
        private Long weightGrams;
    }
}
