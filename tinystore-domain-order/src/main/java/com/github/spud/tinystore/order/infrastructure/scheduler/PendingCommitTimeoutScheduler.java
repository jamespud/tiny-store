package com.github.spud.tinystore.order.infrastructure.scheduler;

import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 兜底：PROMOTION_COMMIT 回执丢失/延迟时，PENDING 超时(>30s)订单自动取消。
 * <p>
 * 并发安全（TOCTOU 缓解，方案 b）：
 * <ol>
 *   <li>查询只筛选 createdAt &lt; threshold 的 PENDING+UNPAID tradeId（SQL 层初筛）；</li>
 *   <li>对每个 tradeId 调 autoCancelTrade 前再次按主键读取并校验状态
 *       （PENDING + UNPAID + 未关闭），状态已变化（如回执 consumer 刚标记
 *       COMMITTED/FAILED、已支付或已关闭）则跳过。</li>
 * </ol>
 * 残余窗口：二次校验与 autoCancelTrade 之间状态仍可能变化，由 autoCancelTrade
 * 内部 closeTrade()（已关闭则抛错）兜底；极端窗口（刚 COMMITTED 即被取消）由
 * 回执幂等逻辑（applyPromotionCommitResult 对已关闭 trade 忽略）保证不双写。
 * <p>
 * 仅在异步 promotion commit 启用（order.promotion.commit-async-enabled=true）时
 * 生效；同步 commit 模式下 PENDING 是合法持久状态（无回执标记 COMMITTED），
 * 调度器为 no-op，避免误取消全部未支付订单。
 */
@Slf4j
@Component
public class PendingCommitTimeoutScheduler {

    private final TradeRepository tradeRepository;
    private final TradeApplicationService tradeApplicationService;

    @Value("${order.promotion.commit-async-enabled:false}")
    private boolean promotionCommitAsyncEnabled;

    @org.springframework.beans.factory.annotation.Value(
            "${order.promotion.pending-timeout-batch-size:200}")
    private int batchSize;

    @org.springframework.beans.factory.annotation.Value(
            "${order.promotion.pending-timeout-seconds:30}")
    private long pendingTimeoutSeconds;

    public PendingCommitTimeoutScheduler(TradeRepository tradeRepository,
                                         TradeApplicationService tradeApplicationService) {
        this.tradeRepository = tradeRepository;
        this.tradeApplicationService = tradeApplicationService;
    }

    @Scheduled(fixedDelayString = "${order.promotion.pending-timeout-check-ms:10000}")
    public void autoCancelStalePendingTrades() {
        if (!promotionCommitAsyncEnabled) {
            return;
        }
        List<String> staleTradeIds = tradeRepository.findStalePendingCommit(
                LocalDateTime.now().minusSeconds(pendingTimeoutSeconds));
        int processed = 0;
        for (String tradeId : staleTradeIds) {
            if (processed >= batchSize) {
                log.warn("Pending timeout batch limit reached ({}), deferring {} remaining trades",
                        batchSize, staleTradeIds.size() - processed);
                break;
            }
            processed++;
            try {
                // 双检查：回执 consumer / 支付回调可能已推进状态，跳过状态已变化的 trade
                Trade trade = tradeRepository.findByTradeId(tradeId).orElse(null);
                if (trade == null || trade.isClosed()
                        || !"PENDING".equals(trade.getPromotionCommitStatus())
                        || trade.getPayStatus() != PayStatus.UNPAID) {
                    log.info("Stale pending trade state changed, skip auto-cancel: tradeId={}",
                            tradeId);
                    continue;
                }
                log.warn("Pending promotion commit timeout, auto-cancelling: tradeId={}", tradeId);
                tradeApplicationService.autoCancelTrade(tradeId,
                        "PROMOTION_COMMIT_TIMEOUT", "timeout-" + tradeId);
            } catch (Exception e) {
                log.error("Timeout auto-cancel failed: tradeId={}", tradeId, e);
            }
        }
    }
}
