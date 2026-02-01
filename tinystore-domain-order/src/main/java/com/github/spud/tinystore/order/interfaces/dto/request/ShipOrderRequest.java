package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 商家发货请求
 */
@Data
public class ShipOrderRequest {
    private String packageId;
    private String waybillNo;
    private String logistics;
    private String traceId;
}
