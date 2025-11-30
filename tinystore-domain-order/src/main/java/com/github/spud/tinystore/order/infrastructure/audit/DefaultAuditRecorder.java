package com.github.spud.tinystore.order.infrastructure.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认审计记录器：先写日志，后续可扩展为持久化。
 */
@Slf4j
@Component
public class DefaultAuditRecorder implements AuditRecorder {
    @Override
    public void record(AuditEntry entry) {
        if (entry == null) return;
        try {
            log.info("AUDIT|ts={}|cmd={}|orderId={}|actorId={}|tenantId={}|traceId={}|success={}|errorCode={}",
                entry.getTimestamp(), entry.getCommandName(), entry.getOrderId(),
                entry.getActorId(), entry.getTenantId(), entry.getTraceId(), entry.isSuccess(), entry.getErrorCode());
        } catch (Exception e) {
            log.warn("Failed to write audit entry: {}", e.getMessage());
        }
    }
}
