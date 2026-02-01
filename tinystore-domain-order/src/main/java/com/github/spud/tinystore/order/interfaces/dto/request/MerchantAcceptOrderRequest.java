package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 商家接受订单请求
 */
@Data
public class MerchantAcceptOrderRequest {
    private String traceId;
}
