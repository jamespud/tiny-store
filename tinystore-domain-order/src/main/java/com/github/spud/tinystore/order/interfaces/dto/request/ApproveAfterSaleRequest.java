package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 批准售后请求
 */
@Data
public class ApproveAfterSaleRequest {
    private String approvalNotes;
    private String traceId;
}
