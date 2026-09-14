package com.github.spud.tinystore.order.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.infrastructure.rpc.payment.PaymentClient;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.ShopOrderJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;

/**
 * Review P1: the timeout scheduler used to close the pay order <b>before</b> entering the claimed cancel
 * path, so two scheduler replicas could both drive an external call for the same trade. The claim must be
 * taken before any external call, which means a lost claim has to stop the pay-side close too.
 */
@DisplayName("order timeout scheduler — claim before external calls")
class OrderTimeoutSchedulerClaimOrderTest {

    private TradeApplicationService tradeApplicationService;
    private PaymentClient paymentClient;
    private OrderTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        tradeApplicationService = mock(TradeApplicationService.class);
        paymentClient = mock(PaymentClient.class);

        TradeJpaRepository tradeRepository = mock(TradeJpaRepository.class);
        when(tradeRepository.findPendingPaymentsByTimeout(anyString(), any(LocalDateTime.class)))
            .thenReturn(List.of(TradeEntity.builder()
                .tradeId("trade-timeout-1")
                .payStatus("UNPAID")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build()));

        PaymentIntentJpaRepository paymentIntentRepository = mock(PaymentIntentJpaRepository.class);
        when(paymentIntentRepository.findByTradeId("trade-timeout-1"))
            .thenReturn(Optional.of(PaymentIntentEntity.builder()
                .paymentId("pay-timeout-1")
                .tradeId("trade-timeout-1")
                .amountCents(1000L)
                .status("PENDING")
                .build()));

        scheduler = new OrderTimeoutScheduler();
        ReflectionTestUtils.setField(scheduler, "tradeJpaRepository", tradeRepository);
        ReflectionTestUtils.setField(scheduler, "tradeApplicationService", tradeApplicationService);
        ReflectionTestUtils.setField(scheduler, "paymentIntentJpaRepository", paymentIntentRepository);
        ReflectionTestUtils.setField(scheduler, "paymentClient", paymentClient);
        ReflectionTestUtils.setField(scheduler, "shopOrderJpaRepository", mock(ShopOrderJpaRepository.class));
        ReflectionTestUtils.setField(scheduler, "paymentTimeoutSeconds", 900L);
    }

    @Test
    @DisplayName("a lost cancel claim stops the pay-order close (no external call without the claim)")
    void lostClaimPreventsPayClose() throws Exception {
        doThrow(new DomainConflictException("CANCEL_IN_PROGRESS", "another instance is cancelling"))
            .when(tradeApplicationService)
            .cancelTrade(anyString(), any());

        assertThatCode(() -> scheduler.closeExpiredPaymentOrders()).doesNotThrowAnyException();

        verify(paymentClient, never()).closePayOrder(anyString());
    }

    @Test
    @DisplayName("a successful cancellation still closes the pay order (control)")
    void successfulCancelClosesPayOrder() throws Exception {
        scheduler.closeExpiredPaymentOrders();

        verify(tradeApplicationService).cancelTrade(anyString(), any());
        verify(paymentClient).closePayOrder("pay-timeout-1");
    }
}
