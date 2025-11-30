package com.github.spud.tinystore.order.domain.statemachine;

import com.github.spud.tinystore.order.domain.statemachine.action.AbstractApplyAction;
import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import com.github.spud.tinystore.order.infrastructure.audit.AuditEntry;
import com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.statemachine.StateContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 覆盖 AbstractApplyAction 成功及失败路径：
 * 1. 成功路径应记录成功审计且不增加失败指标
 * 2. 失败路径应记录失败审计且增加失败指标
 */
public class AbstractApplyActionTest {

    private SimpleMeterRegistry registry;
    private OrderMetrics metrics;
    private AuditRecorder recorder;
    private ObjectProvider<AuditRecorder> provider;
    private StateContext<CoreFlowStatus, OrderEvent> ctx;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        metrics = new OrderMetrics(registry);
        recorder = Mockito.mock(AuditRecorder.class);
        provider = new ObjectProvider<>() {
            @Override public AuditRecorder getObject(Object... args) { return recorder; }
            @Override public AuditRecorder getIfAvailable() { return recorder; }
            @Override public AuditRecorder getIfUnique() { return recorder; }
            @Override public AuditRecorder getObject() { return recorder; }
        };
        // 创建带有 orderId header 的 StateContext mock
        @SuppressWarnings("unchecked")
        StateContext<CoreFlowStatus, OrderEvent> mockCtx = (StateContext<CoreFlowStatus, OrderEvent>) Mockito.mock(StateContext.class);
        this.ctx = mockCtx;
        org.springframework.messaging.MessageHeaders headers = new org.springframework.messaging.MessageHeaders(Map.of("orderId", "ORDER-123"));
        when(ctx.getMessageHeaders()).thenReturn(headers);
        MDC.put("tenantId", "TENANT-1");
        MDC.put("actorId", "ACTOR-9");
        MDC.put("traceId", "TRACE-XYZ");
    }

    @Test
    void testActionSuccessRecordsAuditSuccess() {
        OutboxEventService successService = new OutboxEventService(null, new ObjectMapper()) {
            @Override
            public Map<String, Object> buildEvent(String eventType, String aggregateId, String subOrderId, String tenantId, String operatorId, Object domainData) {
                return Map.of("ok", true);
            }
        };
        AbstractApplyAction action = new AbstractApplyAction(successService, provider, metrics, "order.test.success", Map.of("x", 1)) {};
        action.execute(ctx);

        // 审计记录捕获
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(recorder, times(1)).record(captor.capture());
        AuditEntry entry = captor.getValue();
        assertEquals("order.test.success", entry.getCommandName());
        assertEquals("ORDER-123", entry.getOrderId());
        assertTrue(entry.isSuccess());
        assertNull(entry.getErrorCode());
        // 失败指标未增加
        assertEquals(0.0, registry.get("order_state_machine_failure_total").counter().count());
    }

    @Test
    void testActionFailureIncrementsMetricAndRecordsAuditFailure() {
        OutboxEventService failingService = new OutboxEventService(null, new ObjectMapper()) {
            @Override
            public Map<String, Object> buildEvent(String eventType, String aggregateId, String subOrderId, String tenantId, String operatorId, Object domainData) {
                throw new RuntimeException("boom");
            }
        };
        AbstractApplyAction action = new AbstractApplyAction(failingService, provider, metrics, "order.test.fail", Map.of()) {};
        action.execute(ctx);

        // 审计记录捕获
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(recorder, times(1)).record(captor.capture());
        AuditEntry entry = captor.getValue();
        assertEquals("order.test.fail", entry.getCommandName());
        assertFalse(entry.isSuccess());
        assertEquals("RuntimeException", entry.getErrorCode());
        // 失败指标增加
        assertEquals(1.0, registry.get("order_state_machine_failure_total").counter().count());
    }
}
