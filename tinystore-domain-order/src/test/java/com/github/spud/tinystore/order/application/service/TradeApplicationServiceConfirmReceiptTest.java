package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.testsupport.PassthroughStateTransitionRetry;

/**
 * C8：确认收货只有推进了**所有**子单才允许对外报告成功；出现无法推进的子单必须报冲突，
 * 不能静默返回 200（此前 26 次 E2E 中出现过 1 次"接口 200、订单卡在待收货"）。
 */
@DisplayName("TradeApplicationService — 确认收货的部分推进（C8）")
@ExtendWith(MockitoExtension.class)
class TradeApplicationServiceConfirmReceiptTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ShopOrderRepository shopOrderRepository;

    @Mock
    private OutboxEventService outboxEventService;

    private TradeApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TradeApplicationService();
        ReflectionTestUtils.setField(service, "tradeRepository", tradeRepository);
        ReflectionTestUtils.setField(service, "shopOrderRepository", shopOrderRepository);
        ReflectionTestUtils.setField(service, "outboxEventService", outboxEventService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "optimisticRetryTemplate",
                new PassthroughStateTransitionRetry());
    }

    private static ShopOrder order(String id, OrderStatus status) {
        return ShopOrder.builder()
                .orderId(id)
                .tradeId("trade-1")
                .shopId("SHOP_A")
                .sellerId("seller-A")
                .orderStatus(status)
                .build();
    }

    @Test
    @DisplayName("部分子单无法推进时必须报冲突（不得静默 200）")
    void partialAdvance_shouldThrowConflict() {
        when(tradeRepository.findByTradeId("trade-1"))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Trade.class)));
        // 第一个子单可推进，第二个仍停留在 PENDING_SHIP（例如发货尚未生效）
        when(shopOrderRepository.findByTradeId("trade-1")).thenReturn(List.of(
                order("order-ok", OrderStatus.PENDING_RECEIVE),
                order("order-stuck", OrderStatus.PENDING_SHIP)));
        lenient().when(shopOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.confirmTradeReceipt("trade-1", "trace-1"))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("not fully confirmed")
                .hasMessageContaining("order-stuck");
    }

    @Test
    @DisplayName("全部子单都已推进时不报冲突")
    void allAdvanced_shouldSucceed() {
        when(tradeRepository.findByTradeId("trade-1"))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Trade.class)));
        when(shopOrderRepository.findByTradeId("trade-1")).thenReturn(List.of(
                order("order-a", OrderStatus.PENDING_RECEIVE),
                order("order-b", OrderStatus.SUCCESS)));
        lenient().when(shopOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> service.confirmTradeReceipt("trade-1", "trace-1"))
                .doesNotThrowAnyException();
    }
}
