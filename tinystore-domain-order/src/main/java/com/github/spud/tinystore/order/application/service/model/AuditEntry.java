package com.github.spud.tinystore.order.application.service.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditEntry {
    private long timestamp;
    private String commandName;
    private String orderId;
    private String subOrderId;
    private String actorId;
    private String tenantId;
    private String traceId;
    private String status; // SUCCESS or FAILURE
    private String errorCode; // optional
}
