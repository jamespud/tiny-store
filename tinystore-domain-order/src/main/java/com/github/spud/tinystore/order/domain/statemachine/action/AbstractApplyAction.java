package com.github.spud.tinystore.order.domain.statemachine.action;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import com.github.spud.tinystore.order.infrastructure.audit.AuditEntry;
import com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;

@Slf4j
public abstract class AbstractApplyAction implements Action<CoreFlowStatus, OrderEvent> {

    private final OutboxEventService outboxEventService;
    private final ObjectProvider<AuditRecorder> auditRecorderProvider;
    private final OrderMetrics orderMetrics;
    private final String eventType;
    private final Map<String,Object> domainData;

    protected AbstractApplyAction(OutboxEventService outboxEventService,
                                  ObjectProvider<AuditRecorder> auditRecorderProvider,
                                  OrderMetrics orderMetrics,
                                  String eventType,
                                  Map<String,Object> domainData) {
        this.outboxEventService = outboxEventService;
        this.auditRecorderProvider = auditRecorderProvider;
        this.orderMetrics = orderMetrics;
        this.eventType = eventType;
        this.domainData = domainData == null ? Map.of() : domainData;
    }

    @Override
    public void execute(StateContext<CoreFlowStatus, OrderEvent> context) {
        String orderId = header(context, "orderId", "UNKNOWN");
        String tenantId = MDC.get("tenantId");
        String actorId = MDC.get("actorId");
        try {
            var envelope = outboxEventService.buildEvent(eventType, orderId, null, tenantId, actorId, domainData);
            log.debug("StateMachine action executed: type={}, orderId={}, envelopeKeys={}", eventType, orderId, envelope.keySet());
            recordAudit(orderId, actorId, true, null);
        } catch (Exception e) {
            log.warn("StateMachine action failed: type={}, orderId={}, error={}", eventType, orderId, e.getMessage());
            orderMetrics.incrementStateMachineFailure();
            recordAudit(orderId, actorId, false, e.getClass().getSimpleName());
        }
    }

    private String header(StateContext<CoreFlowStatus, OrderEvent> ctx, String key, String def) {
        Object v = ctx.getMessageHeaders().get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private void recordAudit(String orderId, String actorId, boolean ok, String errorCode) {
        AuditRecorder recorder = auditRecorderProvider.getIfAvailable(() -> null);
        if (recorder == null) return;
        String traceId = MDC.get("traceId");
        String tenantId = MDC.get("tenantId");
        AuditEntry entry = new AuditEntry(System.currentTimeMillis(), eventType, orderId, actorId, tenantId, traceId, ok, errorCode);
        try { recorder.record(entry); } catch (Exception ignore) {}
    }
}