package com.github.spud.tinystore.order.infrastructure.metrics;

import com.github.spud.tinystore.order.application.service.IdempotencyStorage;
import com.github.spud.tinystore.order.application.service.OrderApplicationService;
import com.github.spud.tinystore.order.interfaces.dto.request.MerchantAcceptRequest;
import com.github.spud.tinystore.order.interfaces.rest.MerchantOrderController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsEmissionTest {
  @Test
  void idempotency_hit_and_miss_incremented() {
    SimpleMeterRegistry reg = new SimpleMeterRegistry();
    OrderMetrics metrics = new OrderMetrics(reg);

    IdempotencyStorage storage = new IdempotencyStorage() {
      private final java.util.Map<String,Object> map = new java.util.HashMap<>();
      @Override public boolean exists(String key) { return map.containsKey(key); }
      @Override public <T> T getResponse(String key, Class<T> type) { return type.cast(map.get(key)); }
      @Override public void saveResponse(String key, Object value) { map.put(key, value); }
      @Override public void evict(String key) { map.remove(key); }
    };
    var appSvc = Mockito.mock(OrderApplicationService.class);

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

    MerchantOrderController controller = new MerchantOrderController(appSvc, idpProvider, metricsProvider);

    var req = new MerchantAcceptRequest();
    req.setOrderId("ORDER-X");
    req.setOperatorId("OP-1");

    jakarta.servlet.http.HttpServletRequest http = Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
    Mockito.when(http.getHeader("X-Idempotency-Key")).thenReturn("merchant_accept:ORDER-X:OP-1");

    controller.receiveOrder(req, http); // miss + hit
    controller.receiveOrder(req, http); // hit

    double hit = reg.get("order_idempotency_hit_total").counter().count();
    double miss = reg.get("order_idempotency_miss_total").counter().count();

    assertThat(hit).isGreaterThanOrEqualTo(2.0);
    assertThat(miss).isGreaterThanOrEqualTo(1.0);
  }
}
