package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建交易请求
 */
@Data
public class CreateTradeRequest {
    
    private String tradeId;
    private String buyerId;
    private String buyerNick;
    private String addressId;
    
    // 旧字段：单券兼容，将被视为 platformCouponCodes 的第一张
    private String couponCode;
    
    // 新字段：多券支持
    private List<String> platformCouponCodes;
    private Map<String, List<String>> shopCouponCodesByShop;
    
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
