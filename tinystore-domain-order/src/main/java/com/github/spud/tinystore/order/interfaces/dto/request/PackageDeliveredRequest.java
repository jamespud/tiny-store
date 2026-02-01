package com.github.spud.tinystore.order.interfaces.dto.request;

import lombok.Data;

/**
 * 包裹签收请求
 */
@Data
public class PackageDeliveredRequest {
    private String traceId;
}
