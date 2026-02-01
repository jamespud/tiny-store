package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商家订单数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantOrderData {
    private String orderId;
    private String tradeId;
    private String shopId;
    private String sellerId;
    private String orderStatus;
    private String createdAt;
}
