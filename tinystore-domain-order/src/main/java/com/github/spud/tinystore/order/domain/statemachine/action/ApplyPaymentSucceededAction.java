package com.github.spud.tinystore.order.domain.statemachine.action;

import com.github.spud.tinystore.order.domain.event.OrderEventTypeConstants;
import com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import org.springframework.beans.factory.ObjectProvider;

public class ApplyPaymentSucceededAction extends AbstractApplyAction {

  public ApplyPaymentSucceededAction(OutboxEventService outboxEventService,
    ObjectProvider<AuditRecorder> auditRecorderProvider,
    OrderMetrics orderMetrics) {
    super(outboxEventService, auditRecorderProvider, orderMetrics,
      OrderEventTypeConstants.PAYMENT_SUCCEEDED, java.util.Map.of("phase", "payment"));
  }
}