package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建交易响应数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTradeData {
    private String tradeId;
    private Long payableAmountCents;
    private String paymentIntentId;
}
