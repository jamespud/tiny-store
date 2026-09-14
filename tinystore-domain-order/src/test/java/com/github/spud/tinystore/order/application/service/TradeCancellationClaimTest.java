package com.github.spud.tinystore.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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

        // P0-2 wired the repository into the service; these tests never call createTrade, so an unstubbed
        // mock is all that is needed (stubbing it here would be an unnecessary stubbing under strict stubs).
        ReflectionTestUtils.setField(service, "tradeIdempotencyRecordRepository",
                mock(com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.JpaTradeIdempotencyRecordRepository.class));

        ReflectionTestUtils.setField(service, "optimisticRetryTemplate",
                new PassthroughStateTransitionRetry());
    }

    @Test
    @DisplayName("P1: 取消正在进行（IN_PROGRESS）不能当成功返回 —— 必须让调用方知道还没取消")
    void whenCancellationInProgress_shouldConflictInsteadOfReportingSuccess() {
        lenient().when(idempotencyService.acquire(anyString(), anyString(), anyString()))
                .thenReturn(IdempotencyService.AcquireResult.IN_PROGRESS);

        // 另一个实例还在跑；此刻返回 200 “Trade cancelled” 是假成功（它可能随后回滚）。
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.autoCancelTrade("trade-claim-1", "PROMOTION_COMMIT_FAILED:x", "trace-1"));
        // autoCancelTrade 会包一层 IllegalStateException；根因是 CANCEL_IN_PROGRESS。
        // REST 取消入口不包这一层，同样的异常直接由 GlobalExceptionHandler 映射成 409。
        assertThat(thrown).hasRootCauseInstanceOf(
                com.github.spud.tinystore.order.domain.exception.DomainConflictException.class);
        assertThat(thrown.getCause())
                .isInstanceOf(com.github.spud.tinystore.order.domain.exception.DomainConflictException.class)
                .hasMessageContaining("already in progress");

        // 认领键必须是 trade 派生的稳定值（原先 autoCancelTrade 用 System.nanoTime() 拼随机键）
        verify(idempotencyService).acquire(anyString(), argThat(k -> "trade-cancel:trade-claim-1".equals(k)),
                anyString());
        // 未取得处理权 => 不得触碰交易、子单或 outbox
        verify(tradeRepository, never()).save(any());
        verify(shopOrderRepository, never()).save(any());
        verifyNoInteractions(outboxEventService);
    }

    @Test
    @DisplayName("P1: 已完成的取消（REPLAY）才是幂等成功，直接跳过且无副作用")
    void whenCancellationAlreadyCompleted_shouldSkipAsIdempotentSuccess() {
        lenient().when(idempotencyService.acquire(anyString(), anyString(), anyString()))
                .thenReturn(IdempotencyService.AcquireResult.REPLAY);

        assertThatCode(() -> service.autoCancelTrade("trade-claim-replay", "PROMOTION_COMMIT_FAILED:x",
                "trace-replay")).doesNotThrowAnyException();

        verify(tradeRepository, never()).save(any());
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
