package com.github.spud.tinystore.order.domain.statemachine.action;

import com.github.spud.tinystore.order.domain.event.OrderEventTypeConstants;
import com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import org.springframework.beans.factory.ObjectProvider;

public class ApplyMerchantAcceptedAction extends AbstractApplyAction {

  public ApplyMerchantAcceptedAction(OutboxEventService outboxEventService,
    ObjectProvider<AuditRecorder> auditRecorderProvider,
    OrderMetrics orderMetrics) {
    super(outboxEventService, auditRecorderProvider, orderMetrics,
      OrderEventTypeConstants.ORDER_LIFECYCLE_CHANGED, java.util.Map.of("to", "ACCEPTED"));
  }
}