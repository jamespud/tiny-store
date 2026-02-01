package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 申请售后请求
 */
@Data
public class ApplyAfterSaleRequest {
    private String caseId;
    private String tradeId;
    private String orderId;
    private String afterSaleType;
    private String reason;
    private String traceId;
}
