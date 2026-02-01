package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 取消交易请求
 */
@Data
public class CancelTradeRequest {
    private String reason;
    private String traceId;
}
