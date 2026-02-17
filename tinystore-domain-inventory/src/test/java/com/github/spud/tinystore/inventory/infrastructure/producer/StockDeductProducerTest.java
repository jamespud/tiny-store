package com.github.spud.tinystore.inventory.infrastructure.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.inventory.infrastructure.event.dto.StockDeductMessage;
import com.github.spud.tinystore.inventory.infrastructure.event.dto.StockReleaseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * StockDeductProducer 单元测试
 * <p>
 * 测试范围：
 * - 扣减消息发送逻辑（topic/key/value 正确性）
 * - 释放消息发送逻辑
 * - 异常处理（null 参数）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockDeductProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private StockDeductProducer producer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Mock kafkaTemplate.send() 返回成功的 CompletableFuture
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
    }

    @Test
    void testPublishDeduct_success() throws Exception {
        // Arrange
        StockDeductMessage message = StockDeductMessage.builder()
                .orderId("ORDER123")
                .idempotencyKey("IDEM_KEY")
                .items(List.of(
                        StockDeductMessage.DeductItem.builder()
                                .shopId("SHOP1")
                                .skuId("SKU001")
                                .quantity(2)
                                .build()
                ))
                .build();

        // Act
        producer.publishDeduct(message);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        // 验证 topic
        assertThat(topicCaptor.getValue()).isEqualTo("stock-deduct");

        // 验证 partition key（应该是 orderId）
        assertThat(keyCaptor.getValue()).isEqualTo("ORDER123");

        // 验证消息内容（JSON）
        String json = valueCaptor.getValue();
        StockDeductMessage parsed = objectMapper.readValue(json, StockDeductMessage.class);
        assertThat(parsed.getOrderId()).isEqualTo("ORDER123");
        assertThat(parsed.getIdempotencyKey()).isEqualTo("IDEM_KEY");
        assertThat(parsed.getItems()).hasSize(1);
        assertThat(parsed.getItems().get(0).getSkuId()).isEqualTo("SKU001");
    }

    @Test
    void testPublishRelease_success() throws Exception {
        // Arrange
        StockReleaseMessage message = StockReleaseMessage.builder()
                .orderId("ORDER456")
                .reason("ORDER_CANCELLED")
                .occupyPairs(List.of(
                        StockReleaseMessage.OccupyPairDto.builder()
                                .shopId("SHOP1")
                                .skuId("SKU001")
                                .occupyId("uuid-1")
                                .build()
                ))
                .build();

        // Act
        producer.publishRelease(message);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        // 验证 topic
        assertThat(topicCaptor.getValue()).isEqualTo("stock-release");

        // 验证 partition key
        assertThat(keyCaptor.getValue()).isEqualTo("ORDER456");

        // 验证消息内容
        String json = valueCaptor.getValue();
        StockReleaseMessage parsed = objectMapper.readValue(json, StockReleaseMessage.class);
        assertThat(parsed.getOrderId()).isEqualTo("ORDER456");
        assertThat(parsed.getReason()).isEqualTo("ORDER_CANCELLED");
        assertThat(parsed.getOccupyPairs()).hasSize(1);
        assertThat(parsed.getOccupyPairs().get(0).getOccupyId()).isEqualTo("uuid-1");
    }

    @Test
    void testPublishDeduct_nullMessage_shouldNotSend() {
        // Act
        producer.publishDeduct(null);

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishDeduct_nullOrderId_shouldNotSend() {
        // Arrange
        StockDeductMessage message = StockDeductMessage.builder()
                .orderId(null)
                .items(List.of())
                .build();

        // Act
        producer.publishDeduct(message);

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishRelease_nullMessage_shouldNotSend() {
        // Act
        producer.publishRelease(null);

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishRelease_nullOrderId_shouldNotSend() {
        // Arrange
        StockReleaseMessage message = StockReleaseMessage.builder()
                .orderId(null)
                .occupyPairs(List.of())
                .build();

        // Act
        producer.publishRelease(message);

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}
