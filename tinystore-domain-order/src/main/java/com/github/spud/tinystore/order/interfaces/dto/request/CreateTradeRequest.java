package com.github.spud.tinystore.order.interfaces.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    
    @Valid
    @NotEmpty(message = "orderLines is required and cannot be empty")
    private List<OrderLineItem> orderLines;
    
    @Data
    public static class OrderLineItem {
        @NotBlank(message = "skuId is required")
        private String skuId;
        
        private String productId;
        private String productName;
        
        @NotBlank(message = "shopId is required")
        private String shopId;
        
        @NotBlank(message = "sellerId is required")
        private String sellerId;
        
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;
        
        @NotNull(message = "priceCents is required")
        @Min(value = 0, message = "priceCents must be non-negative")
        private Long priceCents;
        
        private Long weightGrams;
    }
}
