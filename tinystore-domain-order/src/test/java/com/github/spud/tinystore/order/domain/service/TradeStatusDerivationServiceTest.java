package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ShopOrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TradeStatusDerivationService 单元测试
 */
@DisplayName("交易状态推导服务测试")
class TradeStatusDerivationServiceTest {

    private TradeStatusDerivationService service;

    @BeforeEach
    void setUp() {
        service = new TradeStatusDerivationService();
    }

    @Test
    @DisplayName("closedAt 不为空时优先返回 CLOSED（即使 payStatus 为 UNPAID）")
    void testClosedAt_PriorityOverPayStatus_Unpaid() {
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-001")
            .payStatus("UNPAID")
            .closedAt(LocalDateTime.now())
            .build();

        TradeStatusDerivationService.TradeViewStatus status = 
            service.deriveViewStatus(trade, Collections.emptyList(), null, null);

        assertThat(status).isEqualTo(TradeStatusDerivationService.TradeViewStatus.CLOSED);
    }

    @Test
    @DisplayName("closedAt 不为空时优先返回 CLOSED（即使 payStatus 为 PAID）")
    void testClosedAt_PriorityOverPayStatus_Paid() {
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-002")
            .payStatus("PAID")
            .closedAt(LocalDateTime.now())
            .build();

        TradeStatusDerivationService.TradeViewStatus status = 
            service.deriveViewStatus(trade, Collections.emptyList(), null, null);

        assertThat(status).isEqualTo(TradeStatusDerivationService.TradeViewStatus.CLOSED);
    }

    @Test
    @DisplayName("closedAt 为空且 payStatus 为 UNPAID 时返回 WAITING_PAYMENT")
    void testClosedAt_Null_UnpaidStatus() {
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-003")
            .payStatus("UNPAID")
            .closedAt(null)
            .build();

        TradeStatusDerivationService.TradeViewStatus status = 
            service.deriveViewStatus(trade, Collections.emptyList(), null, null);

        assertThat(status).isEqualTo(TradeStatusDerivationService.TradeViewStatus.WAITING_PAYMENT);
    }

    @Test
    @DisplayName("closedAt 不为空时优先返回 CLOSED（即使子单状态为 SUCCESS）")
    void testClosedAt_PriorityOverShopOrders() {
        TradeEntity trade = TradeEntity.builder()
            .tradeId("trade-004")
            .payStatus("PAID")
            .closedAt(LocalDateTime.now())
            .build();

        ShopOrderEntity shopOrder = ShopOrderEntity.builder()
            .orderId("order-001")
            .orderStatus("SUCCESS")
            .build();

        TradeStatusDerivationService.TradeViewStatus status = 
            service.deriveViewStatus(trade, Collections.singletonList(shopOrder), null, null);

        assertThat(status).isEqualTo(TradeStatusDerivationService.TradeViewStatus.CLOSED);
    }
}
