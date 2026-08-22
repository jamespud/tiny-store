package com.github.spud.tinystore.order.infrastructure.scheduler;

import com.github.spud.tinystore.order.application.service.TradeApplicationService;
import com.github.spud.tinystore.order.domain.enums.InventoryStatus;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * B1: 支付后库存确认再驱动调度器。
 * <p>
 * 扫描“已支付、未关闭、支付早于去抖阈值”的交易，对其下仍处于 PRE_DEDUCTED
 * （即未收到 INVENTORY_CONFIRMED 回执）的子单重发 INVENTORY_CONFIRM。
 * 配合 B2（confirm 赢过过期）与 outbox/Kafka 重试，使“确认丢失/迟到”在过期释放前自愈。
 */
@Slf4j
@Component
public class InventoryConfirmReconciler {

    private final TradeRepository tradeRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final TradeApplicationService tradeApplicationService;

    @Value("${order.inventory.confirm-redrive-debounce-seconds:30}")
    private long redriveDebounceSeconds;

    public InventoryConfirmReconciler(TradeRepository tradeRepository,
                                      ShopOrderRepository shopOrderRepository,
                                      TradeApplicationService tradeApplicationService) {
        this.tradeRepository = tradeRepository;
        this.shopOrderRepository = shopOrderRepository;
        this.tradeApplicationService = tradeApplicationService;
    }

    @Scheduled(fixedDelayString = "${order.inventory.confirm-redrive-check-ms:5000}")
    public void reconcileUnconfirmedOrders() {
        List<String> paidTradeIds = tradeRepository.findPaidTradeIdsSince(
                LocalDateTime.now().minusSeconds(redriveDebounceSeconds));
        if (paidTradeIds.isEmpty()) {
            return;
        }
        for (String tradeId : paidTradeIds) {
            try {
                List<ShopOrder> pending = shopOrderRepository.findByTradeIdAndInventoryStatus(
                        tradeId, InventoryStatus.PRE_DEDUCTED.getCode());
                for (ShopOrder shopOrder : pending) {
                    tradeApplicationService.redriveInventoryConfirm(shopOrder);
                }
            } catch (Exception e) {
                log.error("Failed to reconcile inventory confirm for tradeId={}, error={}",
                        tradeId, e.getMessage(), e);
            }
        }
    }
}
