package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.application.service.IdempotencyStorage;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import com.github.spud.tinystore.order.interfaces.dto.request.MerchantAcceptRequest;
import com.github.spud.tinystore.infrastructure.vo.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantOrderIdempotencyTest {
  private MerchantOrderController controller;
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
    controller = new MerchantOrderController(appSvc, idpProvider, metricsProvider);
  }

  @Test
  void receive_then_replay_returns_same_ack() {
    jakarta.servlet.http.HttpServletRequest req = Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
    Mockito.when(req.getHeader("X-Idempotency-Key")).thenReturn("merchant_accept:ORDER-M-1:OP-1");

    MerchantAcceptRequest body = new MerchantAcceptRequest();
    body.setOrderId("ORDER-M-1");
    body.setOperatorId("OP-1");

    Response<?> r1 = controller.receiveOrder(body, req);
    Response<?> r2 = controller.receiveOrder(body, req);

    assertThat(r1.getCode()).isEqualTo("OK");
    assertThat(r2.getCode()).isEqualTo("OK");
    assertThat(r2.getData()).isEqualTo(r1.getData());
  }

  @Test
  void ship_then_replay_returns_same_ack() {
    jakarta.servlet.http.HttpServletRequest req = Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
    Mockito.when(req.getHeader("X-Idempotency-Key")).thenReturn("ship:ORDER-M-4:PKG-001");

    com.github.spud.tinystore.order.interfaces.dto.request.ShipOrderRequest body = new com.github.spud.tinystore.order.interfaces.dto.request.ShipOrderRequest();
    body.setOrderId("ORDER-M-4");
    var logistics = new com.github.spud.tinystore.order.interfaces.dto.request.ShipOrderRequest.LogisticsInfo();
    logistics.setCompanyName("ShunFeng");
    logistics.setTrackingNo("PKG-001");
    body.setLogistics(logistics);
    body.setOperatorId("OP-4");

    Response<?> r1 = controller.shipOrder(body, req);
    Response<?> r2 = controller.shipOrder(body, req);

    assertThat(r1.getCode()).isEqualTo("OK");
    assertThat(r2.getCode()).isEqualTo("OK");
    assertThat(r2.getData()).isEqualTo(r1.getData());
  }

  @Test
  void delivered_confirm_then_replay_returns_same_ack() {
    jakarta.servlet.http.HttpServletRequest req = Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
    Mockito.when(req.getHeader("X-Idempotency-Key")).thenReturn("delivered_confirm:ORDER-M-5:PKG-005");

    com.github.spud.tinystore.order.interfaces.dto.request.DeliveredRequest body = new com.github.spud.tinystore.order.interfaces.dto.request.DeliveredRequest();
    body.setOrderId("ORDER-M-5");
    body.setTrackingNo("PKG-005");
    body.setEventId("EVT-DEL-5");
    body.setSource("merchant");

    Response<?> r1 = controller.confirmDelivery(body, req);
    Response<?> r2 = controller.confirmDelivery(body, req);

    assertThat(r1.getCode()).isEqualTo("OK");
    assertThat(r2.getCode()).isEqualTo("OK");
    assertThat(r2.getData()).isEqualTo(r1.getData());
  }
}
