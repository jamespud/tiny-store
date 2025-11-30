package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.IdempotencyStorage;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import com.github.spud.tinystore.order.interfaces.dto.request.CancelRequest;
import com.github.spud.tinystore.order.interfaces.util.IdempotencyHelper;
import com.github.spud.tinystore.infrastructure.vo.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖首次取消与重复取消的幂等回放。
 */
class UserOrderCancelIdempotencyTest {

    private UserOrderController controller;
    private IdempotencyStorage storage;

    @BeforeEach
    void setup() {
        storage = new IdempotencyStorage() {
            private java.util.Map<String,Object> map = new java.util.HashMap<>();
            @Override public boolean exists(String key) { return map.containsKey(key); }
            @Override public <T> T getResponse(String key, Class<T> type) { return type.cast(map.get(key)); }
            @Override public void saveResponse(String key, Object value) { map.put(key, value); }
            @Override public void evict(String key) { map.remove(key); }
        };
        var appSvc = Mockito.mock(OrderApplicationService.class);
        var metrics = Mockito.mock(OrderMetrics.class);
        org.springframework.beans.factory.ObjectProvider<IdempotencyStorage> idpProvider = new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public IdempotencyStorage getObject(Object... args) { return storage; }
            @Override public IdempotencyStorage getIfAvailable() { return storage; }
            @Override public IdempotencyStorage getIfAvailable(java.util.function.Supplier<IdempotencyStorage> supplier) { return storage; }
            @Override public IdempotencyStorage getObject() { return storage; }
        };
        org.springframework.beans.factory.ObjectProvider<OrderMetrics> metricsProvider = new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public OrderMetrics getObject(Object... args) { return metrics; }
            @Override public OrderMetrics getIfAvailable() { return metrics; }
            @Override public OrderMetrics getIfAvailable(java.util.function.Supplier<OrderMetrics> supplier) { return metrics; }
            @Override public OrderMetrics getObject() { return metrics; }
        };
        controller = new UserOrderController(appSvc, idpProvider, metricsProvider);
    }

    @Test
    void first_cancel_then_replay_returns_same() {
        CancelRequest req = new CancelRequest();
        req.setOrderId(java.util.UUID.randomUUID());
        req.setReasonCode("TEST");
        HttpServletRequest httpReq = Mockito.mock(HttpServletRequest.class);
        Mockito.when(httpReq.getHeader(IdempotencyHelper.IDEMPOTENCY_KEY_HEADER)).thenReturn("cancel:test-user:abc123");
        Mockito.when(httpReq.getHeader("X-Correlation-ID")).thenReturn(java.util.UUID.randomUUID().toString());

        Response<Object> r1 = controller.cancelOrder(req, httpReq);
        Response<Object> r2 = controller.cancelOrder(req, httpReq);

        assertThat(r1.getCode()).isEqualTo("OK");
        assertThat(r2.getCode()).isEqualTo("OK");
        assertThat(r2.getData()).isEqualTo(r1.getData());
    }
}
