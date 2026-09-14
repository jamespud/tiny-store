package com.github.spud.tinystore.inventory.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.inventory.domain.service.InventoryAdjustmentDomainService;
import com.github.spud.tinystore.inventory.domain.service.InventoryReservationDomainService;
import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;

/**
 * P0 (orphan reclaim race): a late {@code INVENTORY_RESERVE_DB} event must not create a PRE_DEDUCTED row
 * whose reservation window has already closed.
 *
 * <p>Without this, the orphan cleaner can look at the DB (no row yet), decide the Redis pre-deduction is an
 * orphan, release it -- and then the consumer inserts the row anyway. The result is a DB row in
 * PRE_DEDUCTED with no corresponding Redis admission, i.e. oversell in the making. Refusing expired events
 * is what turns "orphan-check-delay &gt; max reservation TTL" into a real protocol invariant.
 */
@DisplayName("inventory consumer — late INVENTORY_RESERVE_DB events")
class OrderEventConsumerExpiredReserveDbTest {

    private InventoryReservationDomainService reservationDomainService;
    private JpaConsumerEventLogRepository consumerEventLogRepository;
    private OrderEventConsumer consumer;
    private Acknowledgment ack;

    @BeforeEach
    void setUp() {
        reservationDomainService = mock(InventoryReservationDomainService.class);
        consumerEventLogRepository = mock(JpaConsumerEventLogRepository.class);
        when(consumerEventLogRepository.existsByEventIdAndConsumerName(anyString(), anyString()))
            .thenReturn(false);
        consumer = new OrderEventConsumer(reservationDomainService,
            mock(InventoryAdjustmentDomainService.class),
            consumerEventLogRepository,
            mock(InventoryEventPublisher.class),
            new ObjectMapper());
        ack = mock(Acknowledgment.class);
    }

    @Test
    @DisplayName("an expired event is consumed but never creates a reservation")
    void expiredEventIsRefused() throws Exception {
        consumer.handleOrderEvent(reserveDbMessage(OffsetDateTime.now().minusMinutes(1)), ack);

        verify(reservationDomainService, never()).saveReservation(any(), any(), any(), anyInt(), any(),
            any(), any(), any());
        // Consumed deliberately: retrying would only re-open the window the cleaner is about to close.
        verify(consumerEventLogRepository).save(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("a live event still creates the reservation (control)")
    void liveEventIsApplied() throws Exception {
        consumer.handleOrderEvent(reserveDbMessage(OffsetDateTime.now().plusMinutes(5)), ack);

        verify(reservationDomainService).saveReservation(any(), any(), any(), anyInt(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    private static String reserveDbMessage(OffsetDateTime expireAt) {
        String reservationId = "order-1_" + System.currentTimeMillis() + "_1";
        String payload = "{"
            + "\"reservationId\":\"" + reservationId + "\","
            + "\"shopId\":\"SHOP_A\","
            + "\"skuId\":\"SKU_A\","
            + "\"quantity\":1,"
            + "\"tradeId\":\"trade-1\","
            + "\"orderId\":\"order-1\","
            + "\"expireAt\":\"" + expireAt + "\"}";
        return "{"
            + "\"eventId\":\"" + UUID.randomUUID() + "\","
            + "\"eventType\":\"INVENTORY_RESERVE_DB\","
            + "\"aggregateType\":\"ORDER\","
            + "\"aggregateId\":\"order-1\","
            + "\"payload\":" + new ObjectMapper().valueToTree(payload).toString() + ","
            + "\"traceId\":\"trace-1\","
            + "\"occurredAt\":\"" + OffsetDateTime.now() + "\"}";
    }
}
