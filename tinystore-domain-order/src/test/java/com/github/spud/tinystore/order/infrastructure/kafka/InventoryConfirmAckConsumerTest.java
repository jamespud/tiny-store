package com.github.spud.tinystore.order.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaConsumerEventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryConfirmAckConsumerTest {

    @Mock
    private TradeApplicationService tradeApplicationService;

    @Mock
    private JpaConsumerEventLogRepository consumerEventLogRepository;

    private InventoryConfirmAckConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InventoryConfirmAckConsumer(
                new ObjectMapper(), tradeApplicationService, consumerEventLogRepository);
    }

    @Test
    void confirmedAck_shouldMarkOrderInventoryConfirmed() {
        when(consumerEventLogRepository.existsByEventIdAndConsumerName(anyString(), anyString())).thenReturn(false);
        consumer.handleInventoryConfirmAck(
                "{\"eventId\":\"e1\",\"eventType\":\"INVENTORY_CONFIRMED\","
                        + "\"tradeId\":\"t1\",\"orderId\":\"o1\",\"status\":\"CONFIRMED\"}");
        verify(tradeApplicationService).markShopOrderInventoryConfirmed("o1");
    }

    @Test
    void conflictAck_shouldMarkOrderInventoryConflict() {
        when(consumerEventLogRepository.existsByEventIdAndConsumerName(anyString(), anyString())).thenReturn(false);
        consumer.handleInventoryConfirmAck(
                "{\"eventId\":\"e2\",\"eventType\":\"INVENTORY_CONFIRM_CONFLICT\","
                        + "\"tradeId\":\"t1\",\"orderId\":\"o1\",\"status\":\"RESERVATION_CONFLICT\"}");
        verify(tradeApplicationService).markShopOrderInventoryConflict("o1");
    }
}
