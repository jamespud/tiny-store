package com.github.spud.tinystore.order.infrastructure.audit;

public interface AuditRecorder {
    void record(AuditEntry entry);
}
