package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 确认收货请求
 */
@Data
public class ConfirmReceiptRequest {
    private String traceId;
}
