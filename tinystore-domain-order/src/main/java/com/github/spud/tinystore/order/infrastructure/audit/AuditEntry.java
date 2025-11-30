package com.github.spud.tinystore.order.infrastructure.audit;

public class AuditEntry {
    private final long timestamp;
    private final String commandName;
    private final String orderId;
    private final String actorId;
    private final String tenantId;
    private final String traceId;
    private final boolean success;
    private final String errorCode;

    public AuditEntry(long timestamp, String commandName, String orderId, String actorId,
                      String tenantId, String traceId, boolean success, String errorCode) {
        this.timestamp = timestamp;
        this.commandName = commandName;
        this.orderId = orderId;
        this.actorId = actorId;
        this.tenantId = tenantId;
        this.traceId = traceId;
        this.success = success;
        this.errorCode = errorCode;
    }

    public long getTimestamp() { return timestamp; }
    public String getCommandName() { return commandName; }
    public String getOrderId() { return orderId; }
    public String getActorId() { return actorId; }
    public String getTenantId() { return tenantId; }
    public String getTraceId() { return traceId; }
    public boolean isSuccess() { return success; }
    public String getErrorCode() { return errorCode; }
}
