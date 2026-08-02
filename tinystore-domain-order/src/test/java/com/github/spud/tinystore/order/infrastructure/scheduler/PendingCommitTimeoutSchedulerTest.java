package com.github.spud.tinystore.order.infrastructure.scheduler;

import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PendingCommitTimeoutScheduler} — PENDING promotion commit timeout
 * auto-cancel fallback (>30s).
 * <ul>
 *   <li>stale PENDING trades are auto-cancelled via {@code autoCancelTrade}</li>
 *   <li>state re-check (double check, TOCTOU mitigation): trades that became COMMITTED /
 *       closed / non-UNPAID since the scan are skipped</li>
 *   <li>per-trade exception isolation: one failure does not abort the batch</li>
 *   <li>flag {@code order.promotion.commit-async-enabled}=false → scheduler is a no-op
 *       (sync commit mode has no PENDING timeout semantics)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PendingCommitTimeoutScheduler Tests")
class PendingCommitTimeoutSchedulerTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TradeApplicationService tradeApplicationService;

    private PendingCommitTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PendingCommitTimeoutScheduler(tradeRepository, tradeApplicationService);
        // 兜底调度器仅在异步 promotion commit 启用时生效
        ReflectionTestUtils.setField(scheduler, "promotionCommitAsyncEnabled", true);
    }

    @Test
    @DisplayName("stale PENDING trades are auto-cancelled, changed-state trades are skipped (double check)")
    void timeoutScheduler_shouldAutoCancelStalePendingTrades() {
        // Given: 扫描返回 [A 仍为 PENDING, B 状态已变为 COMMITTED]
        when(tradeRepository.findStalePendingCommit(any(LocalDateTime.class)))
                .thenReturn(List.of("trade-A", "trade-B"));
        when(tradeRepository.findByTradeId("trade-A"))
                .thenReturn(Optional.of(trade("trade-A", "PENDING", PayStatus.UNPAID, false)));
        when(tradeRepository.findByTradeId("trade-B"))
                .thenReturn(Optional.of(trade("trade-B", "COMMITTED", PayStatus.UNPAID, false)));

        // When
        scheduler.autoCancelStalePendingTrades();

        // Then: A 被取消，B 因状态已变化被跳过
        verify(tradeApplicationService).autoCancelTrade(eq("trade-A"),
                eq("PROMOTION_COMMIT_TIMEOUT"), anyString());
        verify(tradeApplicationService, never()).autoCancelTrade(eq("trade-B"), anyString(), anyString());

        // 且阈值参数为 now - 30s
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tradeRepository).findStalePendingCommit(thresholdCaptor.capture());
        LocalDateTime expectedThreshold = LocalDateTime.now().minusSeconds(30);
        assertThat(thresholdCaptor.getValue()).isBetween(
                expectedThreshold.minusSeconds(5), expectedThreshold.plusSeconds(5));
    }

    @Test
    @DisplayName("no stale trades found → nothing is auto-cancelled")
    void timeoutScheduler_shouldNotCancelWhenNoStaleTrades() {
        // Given
        when(tradeRepository.findStalePendingCommit(any(LocalDateTime.class))).thenReturn(List.of());

        // When
        scheduler.autoCancelStalePendingTrades();

        // Then
        verify(tradeApplicationService, never()).autoCancelTrade(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("trades that became closed or paid since the scan are skipped (TOCTOU mitigation)")
    void timeoutScheduler_shouldSkipClosedOrPaidTrades() {
        // Given: C 已关闭（回执/超时关闭竞态），D 已支付（正常支付后不再可取消）
        when(tradeRepository.findStalePendingCommit(any(LocalDateTime.class)))
                .thenReturn(List.of("trade-C", "trade-D"));
        when(tradeRepository.findByTradeId("trade-C"))
                .thenReturn(Optional.of(trade("trade-C", "PENDING", PayStatus.UNPAID, true)));
        when(tradeRepository.findByTradeId("trade-D"))
                .thenReturn(Optional.of(trade("trade-D", "PENDING", PayStatus.PAID, false)));

        // When
        scheduler.autoCancelStalePendingTrades();

        // Then: 均跳过，不触发自动取消
        verify(tradeApplicationService, never()).autoCancelTrade(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("one trade's auto-cancel failure is isolated, remaining trades still processed")
    void timeoutScheduler_shouldIsolateExceptions() {
        // Given: X 取消失败，Y 取消成功
        when(tradeRepository.findStalePendingCommit(any(LocalDateTime.class)))
                .thenReturn(List.of("trade-X", "trade-Y"));
        when(tradeRepository.findByTradeId("trade-X"))
                .thenReturn(Optional.of(trade("trade-X", "PENDING", PayStatus.UNPAID, false)));
        when(tradeRepository.findByTradeId("trade-Y"))
                .thenReturn(Optional.of(trade("trade-Y", "PENDING", PayStatus.UNPAID, false)));
        doThrow(new RuntimeException("cancel failed"))
                .when(tradeApplicationService).autoCancelTrade(eq("trade-X"), anyString(), anyString());

        // When/Then: 调度器不抛异常，Y 继续处理
        assertThatCode(scheduler::autoCancelStalePendingTrades).doesNotThrowAnyException();
        verify(tradeApplicationService).autoCancelTrade(eq("trade-Y"),
                eq("PROMOTION_COMMIT_TIMEOUT"), anyString());
    }

    @Test
    @DisplayName("scheduler is a no-op when async promotion commit flag is disabled")
    void timeoutScheduler_shouldSkipWhenFlagDisabled() {
        // Given: flag=false（同步 commit 模式，PENDING 是合法持久状态，不做超时兜底）
        ReflectionTestUtils.setField(scheduler, "promotionCommitAsyncEnabled", false);

        // When
        scheduler.autoCancelStalePendingTrades();

        // Then: 不查询、不取消
        verify(tradeRepository, never()).findStalePendingCommit(any());
        verify(tradeApplicationService, never()).autoCancelTrade(anyString(), anyString(), anyString());
    }

    // ============ helpers ============

    private Trade trade(String tradeId, String promotionCommitStatus, PayStatus payStatus, boolean closed) {
        Trade.TradeBuilder builder = Trade.builder()
                .tradeId(tradeId)
                .buyerId("buyer-" + tradeId)
                .buyerNick("buyer")
                .payStatus(payStatus)
                .promotionCommitStatus(promotionCommitStatus)
                .totalAmountCents(1500L)
                .discountAmountCents(0L)
                .payableAmountCents(1500L)
                .createdAt(LocalDateTime.now().minusSeconds(60))
                .updatedAt(LocalDateTime.now().minusSeconds(60));
        if (closed) {
            builder.closedAt(LocalDateTime.now());
        }
        return builder.build();
    }
}
