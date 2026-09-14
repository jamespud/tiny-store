package com.github.spud.tinystore.order.infrastructure.scheduler;

import com.github.spud.tinystore.infrastructure.rpc.payment.PaymentClient;
import com.github.spud.tinystore.order.application.command.CancelTradeCommand;
import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单超时调度器（支付超时关闭、自动收货等）
 */
@Slf4j
@Service
@EnableScheduling
public class OrderTimeoutScheduler {

    @Autowired
    private TradeJpaRepository tradeJpaRepository;

    @Autowired
    private TradeApplicationService tradeApplicationService;

    @Value("${order.payment-timeout-seconds:900}")
    private long paymentTimeoutSeconds;

    @Value("${order.auto-receive-timeout-seconds:604800}")
    private long autoReceiveTimeoutSeconds;

    @Autowired
    private com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.ShopOrderJpaRepository shopOrderJpaRepository;

    @Autowired
    private PaymentIntentJpaRepository paymentIntentJpaRepository;

    @Autowired
    private PaymentClient paymentClient;

    /**
     * 定时检查支付超时订单并关闭
     * 默认每 1 分钟扫描一次，超时阈值为 900 秒（15 分钟）
     */
    @Scheduled(fixedDelayString = "${order.scheduler.payment-timeout-interval:60000}")
    public void closeExpiredPaymentOrders() {
        try {
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusSeconds(paymentTimeoutSeconds);

            // 查询超时未支付的订单
            List<TradeEntity> expiredTrades = tradeJpaRepository.findPendingPaymentsByTimeout("UNPAID", timeoutThreshold);

            if (expiredTrades == null || expiredTrades.isEmpty()) {
                log.debug("No expired payment trades found");
                return;
            }

            log.info("Found {} expired payment trades, closing them...", expiredTrades.size());

            for (TradeEntity trade : expiredTrades) {
                try {
                    // P1: the cancel claim must be taken BEFORE any external call. cancelTrade() claims
                    // first (stable key `trade-cancel:<tradeId>`), so two scheduler replicas can no longer
                    // both drive the pay-service close for the same trade; the close now happens after the
                    // trade-side cancellation, under that claim.
                    CancelTradeCommand command = CancelTradeCommand.builder()
                        .tradeId(trade.getTradeId())
                        .reason("Payment timeout")
                        .traceId(UUID.randomUUID().toString())
                        .build();

                    tradeApplicationService.cancelTrade(
                        // C6：稳定幂等键。原先用随机 UUID，等于主动放弃去重信号；
                        // 多副本同时扫到同一笔超时订单时会各自执行一次取消。
                        "timeout-cancel:" + trade.getTradeId(),
                        command
                    );

                    // 交易已取消（claim 已取得）后才关闭支付单；这是清理动作，失败不回滚取消。
                    try {
                        var paymentIntent = paymentIntentJpaRepository.findByTradeId(trade.getTradeId());
                        if (paymentIntent.isPresent()) {
                            String paymentIntentId = paymentIntent.get().getPaymentId();
                            paymentClient.closePayOrder(paymentIntentId);
                            log.info("Closed payment order in pay-service: paymentIntentId={}, tradeId={}",
                                paymentIntentId, trade.getTradeId());
                        }
                    } catch (Exception payCloseEx) {
                        log.warn("Failed to close payment order in pay-service (trade is already cancelled): tradeId={}",
                            trade.getTradeId(), payCloseEx);
                    }

                    log.info("Closed expired trade: tradeId={}", trade.getTradeId());

                } catch (Exception e) {
                    log.error("Failed to closeTrade expired trade: tradeId={}", trade.getTradeId(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in closeExpiredPaymentOrders scheduler", e);
        }
    }

    /**
     * 定时自动确认收货
     * 默认每 1 小时扫描一次，超时阈值为 604800 秒（7 天）
     */
    @Scheduled(fixedDelayString = "${order.scheduler.auto-receive-interval:3600000}")
    public void autoReceiveOrders() {
        try {
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusSeconds(autoReceiveTimeoutSeconds);

            // 查询待收货且已超时的订单
            List<com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ShopOrderEntity> expiredOrders = 
                shopOrderJpaRepository.findPendingReceiveByTimeout("PENDING_RECEIVE", timeoutThreshold);

            if (expiredOrders == null || expiredOrders.isEmpty()) {
                log.debug("No expired pending-receive orders found");
                return;
            }

            log.info("Found {} expired pending-receive orders, auto confirming receipt...", expiredOrders.size());

            for (com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ShopOrderEntity order : expiredOrders) {
                try {
                    // 调用确认收货逻辑
                    tradeApplicationService.confirmTradeReceipt(
                        order.getTradeId(),
                        UUID.randomUUID().toString() // traceId
                    );

                    log.info("Auto-received order: orderId={}, tradeId={}", order.getOrderId(), order.getTradeId());

                } catch (Exception e) {
                    log.error("Failed to auto-receive order: orderId={}", order.getOrderId(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in autoReceiveOrders scheduler", e);
        }
    }
}
