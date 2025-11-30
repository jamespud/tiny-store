package com.github.spud.tinystore.order.infrastructure.event.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import com.github.spud.tinystore.order.infrastructure.metrics.OutboxMetrics;
import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderOutboxEventPO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.github.spud.tinystore.order.domain.event.OrderEventTypeConstants;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

  @Mock
  private OutboxEventService outboxEventService;

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  @Mock
  private OutboxMetrics outboxMetrics;

  private ObjectMapper objectMapper;

  @InjectMocks
  private OutboxEventPublisher publisher;

  private ObjectProvider<OrderMetrics> orderMetricsProvider;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    // 提供一个简单的 ObjectProvider<OrderMetrics> 测试桩
    OrderMetrics metrics = Mockito.mock(OrderMetrics.class);
    this.orderMetricsProvider = new ObjectProvider<>() {
      @Override public OrderMetrics getObject(Object... args) { return metrics; }
      @Override public OrderMetrics getIfAvailable() { return metrics; }
      @Override public OrderMetrics getIfAvailable(java.util.function.Supplier<OrderMetrics> supplier) { return metrics; }
      @Override public OrderMetrics getObject() { return metrics; }
    };
    publisher = new OutboxEventPublisher(outboxEventService, kafkaTemplate, objectMapper, outboxMetrics, orderMetricsProvider);
  }

  @Test
  void publishPendingEvents_maps_eventType_and_topic_correctly() throws Exception {
    // given: prepare five events with mixed raw type names
    OrderOutboxEventPO e1 = baseEvent("E1", "ORDER-1", "ORDER_PAID");
    OrderOutboxEventPO e2 = baseEvent("E2", "ORDER-2", "order.payment.succeeded");
    OrderOutboxEventPO e3 = baseEvent("E3", "ORDER-3", "OrderShippedEvent");
    OrderOutboxEventPO e4 = baseEvent("E4", "ORDER-4", "STATUS_CHANGED");
    OrderOutboxEventPO e5 = baseEvent("E5", "ORDER-5", "FOO");

    when(outboxEventService.findPendingEvents(100)).thenReturn(List.of(e1, e2, e3, e4, e5));

    // mock Kafka send to complete successfully
    @SuppressWarnings("unchecked")
    SendResult<String, String> sr = (SendResult<String, String>) Mockito.mock(SendResult.class);
    RecordMetadata rm = Mockito.mock(RecordMetadata.class);
    when(rm.partition()).thenReturn(0);
    when(rm.offset()).thenReturn(0L);
    when(sr.getRecordMetadata()).thenReturn(rm);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sr));

    // when
    publisher.publishPendingEvents();

    // then: capture topics and payloads
    ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> payloadCap = ArgumentCaptor.forClass(String.class);
    verify(kafkaTemplate, times(5)).send(topicCap.capture(), keyCap.capture(), payloadCap.capture());

    List<String> topics = topicCap.getAllValues();
    List<String> payloads = payloadCap.getAllValues();

    // assert topics
    assertThat(topics.get(0)).isEqualTo("tinystore.order.paid");
    assertThat(topics.get(1)).isEqualTo("tinystore.order.paid");
    assertThat(topics.get(2)).isEqualTo("tinystore.order.shipped");
    assertThat(topics.get(3)).isEqualTo("tinystore.order.status-changed");
    assertThat(topics.get(4)).isEqualTo("tinystore.order.general");

    // assert message eventType after mapping
    for (int i = 0; i < payloads.size(); i++) {
      @SuppressWarnings("unchecked")
      Map<String, Object> msg = objectMapper.readValue(payloads.get(i), Map.class);
      String et = (String) msg.get("eventType");
      switch (i) {
          case 0 -> assertThat(et).isEqualTo(OrderEventTypeConstants.PAYMENT_SUCCEEDED);
          case 1 -> assertThat(et).isEqualTo(OrderEventTypeConstants.PAYMENT_SUCCEEDED);
          case 2 -> assertThat(et).isEqualTo(OrderEventTypeConstants.GOODS_SHIPPED);
          case 3 -> assertThat(et).isEqualTo(OrderEventTypeConstants.ORDER_LIFECYCLE_CHANGED);
          case 4 -> assertThat(et).isEqualTo("order.general");
      }
    }

    // verify status updates per success
    verify(outboxEventService, times(5)).markEventsSent(any());
  }

  @Test
  void publishPendingEvents_without_mapping_falls_back_to_general_and_raw_type() throws Exception {
    // given
    OrderOutboxEventPO e1 = baseEvent("E10", "ORDER-10", "ORDER_PAID");
    when(outboxEventService.findPendingEvents(100)).thenReturn(List.of(e1));

    @SuppressWarnings("unchecked")
    SendResult<String, String> sr = (SendResult<String, String>) Mockito.mock(SendResult.class);
    RecordMetadata rm = Mockito.mock(RecordMetadata.class);
    when(rm.partition()).thenReturn(0);
    when(rm.offset()).thenReturn(0L);
    when(sr.getRecordMetadata()).thenReturn(rm);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sr));

    // disable mapping via reflection to simulate feature flag off
    java.lang.reflect.Field f = OutboxEventPublisher.class.getDeclaredField("mapExternalEventType");
    f.setAccessible(true);
    f.set(publisher, false);

    // when
    publisher.publishPendingEvents();

    // then
    ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> payloadCap = ArgumentCaptor.forClass(String.class);
    verify(kafkaTemplate).send(topicCap.capture(), anyString(), payloadCap.capture());
    assertThat(topicCap.getValue()).isEqualTo("tinystore.order.general");

    @SuppressWarnings("unchecked")
    Map<String, Object> msg = objectMapper.readValue(payloadCap.getValue(), Map.class);
    assertThat(msg.get("eventType")).isEqualTo("ORDER_PAID");
  }

  private static OrderOutboxEventPO baseEvent(String id, String orderId, String rawType) {
    return new OrderOutboxEventPO()
        .setId(id)
        .setOrderId(orderId)
        .setEventType(rawType)
        .setEventPayload("{}")
        .setCreatedAt(OffsetDateTime.now());
  }
}
