package com.github.spud.tinystore.order.domain.statemachine.action;

import com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import com.github.spud.tinystore.order.domain.event.OrderEventTypeConstants;
import org.springframework.beans.factory.ObjectProvider;

public class ApplyAfterSaleAppliedAction extends AbstractApplyAction {
    public ApplyAfterSaleAppliedAction(OutboxEventService outboxEventService,
                                       ObjectProvider<AuditRecorder> auditRecorderProvider,
                                       OrderMetrics orderMetrics) {
        super(outboxEventService, auditRecorderProvider, orderMetrics,
            OrderEventTypeConstants.AFTERSALE_APPLIED, java.util.Map.of());
    }
}