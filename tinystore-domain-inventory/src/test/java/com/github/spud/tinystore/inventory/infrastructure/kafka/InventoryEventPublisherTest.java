package com.github.spud.tinystore.inventory.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private InventoryEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new InventoryEventPublisher(kafkaTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(publisher, "topic", "tinystore.inventory.general");
    }

    @Test
    void publishConfirmAck_shouldSendToInventoryTopicWithConfirmedType() throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishConfirmAck("evt-1", "INVENTORY_CONFIRMED", "trade-1", "order-1", "CONFIRMED", null);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("tinystore.inventory.general"), eq("order-1"), valueCaptor.capture());
        assertThat(valueCaptor.getValue())
                .contains("\"eventType\":\"INVENTORY_CONFIRMED\"")
                .contains("\"tradeId\":\"trade-1\"")
                .contains("\"orderId\":\"order-1\"")
                .contains("\"status\":\"CONFIRMED\"");
    }
}
