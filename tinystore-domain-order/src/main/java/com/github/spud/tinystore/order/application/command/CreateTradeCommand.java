package com.github.spud.tinystore.order.application.command;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建交易命令
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTradeCommand {
    private String tradeId;
    private String buyerId;
    private String buyerNick;
    private String addressId;
    
    // 旧字段：单券兼容
    private String couponCode;
    
    // 新字段：多券
    private List<String> platformCouponCodes;
    private Map<String, List<String>> shopCouponCodesByShop;
    
    private List<OrderLineCommand> orderLines;
    private String traceId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderLineCommand {
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
