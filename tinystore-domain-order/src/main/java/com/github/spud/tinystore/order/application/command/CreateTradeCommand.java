package com.github.spud.tinystore.order.application.command;

import java.util.List;
import java.util.Map;

import com.github.spud.tinystore.order.domain.model.OrderLine;
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
        
        public OrderLine toOrderLine() {
            return OrderLine.builder()
                    .skuId(this.skuId)
                    .productId(this.productId)
                    .productName(this.productName)
                    .quantity(this.quantity)
                    .priceCents(this.priceCents)
                    .lineAmountCents(this.priceCents * this.quantity)
                    .build();
        }
    }
}
