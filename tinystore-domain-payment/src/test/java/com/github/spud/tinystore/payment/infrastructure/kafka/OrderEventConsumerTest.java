package com.github.spud.tinystore.payment.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.payment.application.PaymentApplicationService;

/**
 * C1 修复后的消费者契约：成功才 ack，失败必须抛出（交给错误处理器重试/DLT），
 * 毒消息抛 IllegalArgumentException（不可重试 → 直接 DLT）。
 */
@DisplayName("payment OrderEventConsumer — 手动 ack 与失败语义")
class OrderEventConsumerTest {

    private PaymentApplicationService paymentApplicationService;
    private Acknowledgment ack;
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        paymentApplicationService = mock(PaymentApplicationService.class);
        ack = mock(Acknowledgment.class);
        consumer = new OrderEventConsumer();
        ReflectionTestUtils.setField(consumer, "paymentApplicationService", paymentApplicationService);
        ReflectionTestUtils.setField(consumer, "objectMapper", new ObjectMapper());
    }

    private static String envelope(String eventType, String payload) {
        return "{\"eventId\":\"e-1\",\"eventType\":\"" + eventType + "\",\"payload\":\"" + payload + "\"}";
    }

    @Test
    @DisplayName("成功处理 PAYMENT_INTENT_CREATED 后提交 offset")
    void success_acknowledges() {
        String payload = "{\\\"paymentIntentId\\\":\\\"pay-1\\\",\\\"tradeId\\\":\\\"trade-1\\\","
                + "\\\"buyerId\\\":\\\"buyer-1\\\",\\\"amountCents\\\":1000,\\\"payChannel\\\":\\\"WECHAT\\\"}";

        consumer.handleOrderEvent(envelope("PAYMENT_INTENT_CREATED", payload), ack);

        verify(paymentApplicationService).createPaymentOrderFromIntent(
                eq("pay-1"), eq("trade-1"), eq("buyer-1"), eq(1000L), eq("WECHAT"), any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("未知事件类型被忽略但仍然 ack")
    void unknownEventType_isIgnoredAndAcknowledged() {
        consumer.handleOrderEvent(envelope("SOMETHING_ELSE", "{}"), ack);

        verifyNoInteractions(paymentApplicationService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("业务失败必须抛出且不得 ack（交给重试/DLT）")
    void handlerFailure_isRethrownAndNotAcknowledged() {
        String payload = "{\\\"paymentIntentId\\\":\\\"pay-2\\\",\\\"tradeId\\\":\\\"trade-2\\\","
                + "\\\"buyerId\\\":\\\"buyer-2\\\",\\\"amountCents\\\":500}";
        doThrow(new IllegalStateException("db unavailable")).when(paymentApplicationService)
                .createPaymentOrderFromIntent(anyString(), anyString(), anyString(), anyLong(), any(), any());

        assertThatThrownBy(() -> consumer.handleOrderEvent(
                envelope("PAYMENT_INTENT_CREATED", payload), ack))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("毒消息抛 IllegalArgumentException 以便直接进 DLT")
    void poisonMessage_throwsIllegalArgument() {
        assertThatThrownBy(() -> consumer.handleOrderEvent("{not-json", ack))
                .isInstanceOf(IllegalArgumentException.class);

        verify(ack, never()).acknowledge();
    }
}
