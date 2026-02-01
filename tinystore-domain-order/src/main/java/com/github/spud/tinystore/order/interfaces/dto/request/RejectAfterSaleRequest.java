package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 拒绝售后请求
 */
@Data
public class RejectAfterSaleRequest {
    private String rejectionReason;
    private String traceId;
}
