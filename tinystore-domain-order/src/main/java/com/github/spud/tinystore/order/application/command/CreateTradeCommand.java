package com.github.spud.tinystore.order.application.command;

import java.util.List;
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
    private String couponCode;
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
