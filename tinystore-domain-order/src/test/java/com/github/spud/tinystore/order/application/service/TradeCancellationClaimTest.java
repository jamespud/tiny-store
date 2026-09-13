package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.testsupport.PassthroughStateTransitionRetry;

/**
 * C6：取消动作按 trade 认领——两个副本（或两个调度器）同时扫到同一笔超时订单时，
 * 只有一个能取得处理权，另一个必须直接跳过。
 */
@DisplayName("TradeApplicationService — 取消认领（C6）")
@ExtendWith(MockitoExtension.class)
class TradeCancellationClaimTest {

    @Mock
    private IdempotencyService idempotencyService;

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
        ReflectionTestUtils.setField(service, "idempotencyService", idempotencyService);
        ReflectionTestUtils.setField(service, "optimisticRetryTemplate",
                new PassthroughStateTransitionRetry());
    }

    @Test
    @DisplayName("认领使用 trade 派生的稳定键，且另一个持有者存在时直接跳过")
    void whenClaimHeldElsewhere_shouldSkipWithoutSideEffects() {
        lenient().when(idempotencyService.acquire(anyString(), anyString(), anyString()))
                .thenReturn(IdempotencyService.AcquireResult.IN_PROGRESS);

        assertThatCode(() -> service.autoCancelTrade("trade-claim-1", "PROMOTION_COMMIT_FAILED:x",
                "trace-1")).doesNotThrowAnyException();

        // 认领键必须是 trade 派生的稳定值（原先 autoCancelTrade 用 System.nanoTime() 拼随机键）
        verify(idempotencyService).acquire(anyString(), argThat(k -> "trade-cancel:trade-claim-1".equals(k)),
                anyString());
        // 未取得处理权 => 不得触碰交易、子单或 outbox
        verify(tradeRepository, never()).save(any());
        verify(shopOrderRepository, never()).save(any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    @DisplayName("取得处理权时按 trade 派生键继续执行")
    void whenClaimAcquired_shouldProceed() {
        lenient().when(idempotencyService.acquire(anyString(), anyString(), anyString()))
                .thenReturn(IdempotencyService.AcquireResult.ACQUIRED);
        // 交易不存在 -> 抛领域异常，说明已经越过认领、进入真正的取消流程
        lenient().when(tradeRepository.findByTradeId("trade-claim-2"))
                .thenReturn(java.util.Optional.empty());

        assertThatCode(() -> service.autoCancelTrade("trade-claim-2", "PROMOTION_COMMIT_FAILED:x",
                "trace-2")).isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(
                        com.github.spud.tinystore.order.domain.exception.DomainConflictException.class);
    }
}
