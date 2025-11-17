package com.github.spud.tinystore.order.infrastructure.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.event.OrderPaymentSucceededEvent;
import com.github.spud.tinystore.order.infrastructure.persistence.po.OrderOutboxEventPO;
import com.github.spud.tinystore.order.infrastructure.persistence.repository.OrderOutboxEventRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

  @Mock
  private OrderOutboxEventRepository repository;

  private ObjectMapper objectMapper;

  @InjectMocks
  private OutboxEventService service;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    // Manually inject since @InjectMocks won't set final fields without a constructor here
    service = new OutboxEventService(repository, objectMapper);
  }

  @Test
  void saveEvent_builds_envelope_and_persists() throws Exception {
    // given
    OrderPaymentSucceededEvent event = OrderPaymentSucceededEvent.builder()
        .orderId("ORDER-1001")
        .paymentId("PAY-1")
        .amount(new BigDecimal("12.34"))
        .isDeposit(false)
        .isFinalPayment(true)
        .build();
    event.setEventId("EVT-1");
    event.setType(OrderEventType.ORDER_PAID);
    event.setTraceId("TRACE-xyz");

    // when
    service.saveEvent(event);

    // then
    ArgumentCaptor<OrderOutboxEventPO> captor = ArgumentCaptor.forClass(OrderOutboxEventPO.class);
    verify(repository).save(captor.capture());
    OrderOutboxEventPO saved = captor.getValue();

    assertThat(saved.getId()).isEqualTo("EVT-1");
    assertThat(saved.getOrderId()).isEqualTo("ORDER-1001");
    assertThat(saved.getEventType()).isEqualTo("ORDER_PAID");
    assertThat(saved.getTraceId()).isEqualTo("TRACE-xyz");
    assertThat(saved.getStatus()).isEqualTo(OrderOutboxEventPO.OutboxEventStatus.PENDING);
    assertThat(saved.getRetryCount()).isZero();

    // verify envelope payload essentials
    @SuppressWarnings("unchecked")
    Map<String, Object> envelope = objectMapper.readValue(saved.getEventPayload(), Map.class);
    assertThat(envelope.get("version")).isEqualTo("v1");
    assertThat(envelope.get("eventId")).isEqualTo("EVT-1");
    assertThat(envelope.get("aggregateId")).isEqualTo("ORDER-1001");
    assertThat(((Map<?, ?>) envelope.get("operator")).get("type")).isIn("user", "system");
    assertThat(envelope).containsKeys("data");
  }
}
