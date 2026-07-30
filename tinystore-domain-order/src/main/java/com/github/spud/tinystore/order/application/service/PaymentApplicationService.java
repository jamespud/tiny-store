package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.exception.IdempotencyConflictException;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.dto.*;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 支付应用服务（处理退款逻辑）
 */
@Slf4j
@Service
public class PaymentApplicationService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ShopOrderRepository shopOrderRepository;

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private PromotionClient promotionClient;

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理退款请求（售后、用户主动退款等）
     * 步骤：
     * 1. 检查幂等性（以 refundId 为唯一键）
     * 2. 获取 Trade，检查支付状态
     * 3. 调用库存回补（restock）
     * 4. 调用促销释放（release）
     * 5. 更新 Trade 支付状态为 REFUNDED
     * 6. 写入 Outbox 事件
     *
     * @param idempotencyKey 幂等键
     * @param tradeId 交易ID
     * @param refundId 退款ID（唯一标识一次退款请求）
     * @param refundAmountCents 退款金额
     * @param reason 退款原因
     * @param traceId 追踪ID
     */
    @Transactional
    public void processRefund(String idempotencyKey, String tradeId, String refundId, 
                            Long refundAmountCents, String reason, String traceId) throws Exception {
        try {
            // 1. 幂等性检查（refundId 自身就是唯一键）
            String fingerprint = refundId + ":" + tradeId;
            if (!idempotencyService.tryAcquire("payment:refund", idempotencyKey, fingerprint)) {
                String cachedResponse = idempotencyService.getCachedResponse("payment:refund", idempotencyKey);
                if (cachedResponse != null) {
                    log.info("Idempotent refund: cached response found, refundId={}", refundId);
                    return;
                }
                throw new IdempotencyConflictException("REFUND_IDEMPOTENT_CONFLICT",
                    "Refund already in progress with refundId: " + refundId);
            }

            // 2. 获取 Trade 聚合根
            Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new DomainConflictException("TRADE_NOT_FOUND",
                    "Trade not found: " + tradeId));

            // 检查是否已支付
            if (!PayStatus.PAID.equals(trade.getPayStatus())) {
                throw new DomainConflictException("INVALID_REFUND_STATE",
                    "Can only refund PAID trades, current status: " + trade.getPayStatus());
            }

            // 3. 获取所有子单，按 ShopOrder 聚合生成 adjustment items
            List<ShopOrder> shopOrders = shopOrderRepository.findByTradeId(tradeId);

            // 按 shop 写 Outbox INVENTORY_ADJUST 事件 (async, replaces sync Feign adjust)
            for (ShopOrder shopOrder : shopOrders) {
                List<Map<String, Object>> adjustItems = shopOrder.getOrderLines().stream()
                    .map(orderLine -> Map.<String, Object>of(
                        "shopId", shopOrder.getShopId(),
                        "skuId", orderLine.getSkuId(),
                        "delta", (long) orderLine.getQuantity()))
                    .collect(Collectors.toList());

                OrderDomainEvent adjustEvent = OrderDomainEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(OrderEventType.INVENTORY_ADJUST)
                    .aggregateType("INVENTORY")
                    .aggregateId(refundId + ":" + shopOrder.getShopId())
                    .occurredAt(LocalDateTime.now())
                    .traceId(traceId)
                    .payloadJson(objectMapper.writeValueAsString(Map.of(
                            "tradeId", tradeId,
                            "refundId", refundId,
                            "reason", "RESTOCK_REFUND",
                            "items", adjustItems)))
                    .build();
                outboxEventService.saveEvent(adjustEvent);
                log.info("Inventory adjust event written (async): refundId={}, shopId={}",
                    refundId, shopOrder.getShopId());
            }

            // 4. 调用促销释放
            try {
                // 校验 promotionQuoteId 已绑定
                if (trade.getPromotionQuoteId() == null || trade.getPromotionQuoteId().isEmpty()) {
                    log.warn("Missing promotionQuoteId for trade: {}, skipping promotion release", tradeId);
                } else {
                    PromotionReleaseRequest releaseRequest = PromotionReleaseRequest.builder()
                        .quoteId(trade.getPromotionQuoteId())
                        .tradeId(trade.getTradeId())
                        .reason(reason)
                        .build();
                    promotionClient.release(idempotencyKey, releaseRequest);
                    log.info("Promotion release succeeded: refundId={}", refundId);
                }
            } catch (Exception e) {
                log.warn("Promotion release failed for refundId={}, continuing anyway", refundId, e);
                // 不抛异常，继续流程（库存已回补，促销释放可重试）
            }

            // 5. 更新 Trade 支付状态（使用聚合根方法）
            if (refundAmountCents >= trade.getPayableAmountCents()) {
                trade.markAsRefunded();
            } else {
                trade.markAsPartRefunded();
            }
            tradeRepository.save(trade);

            // 6. 写入 Outbox 事件
            OrderDomainEvent refundEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.REFUND_SUCCEEDED)
                .aggregateType("TRADE")
                .aggregateId(trade.getTradeId())
                .occurredAt(LocalDateTime.now())
                .traceId(traceId)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "refundId", refundId,
                    "tradeId", tradeId,
                    "refundAmountCents", refundAmountCents,
                    "reason", reason
                )))
                .build();
            outboxEventService.saveEvent(refundEvent);

            // 缓存幂等响应
            Map<String, Object> response = new HashMap<>();
            response.put("refundId", refundId);
            response.put("status", "SUCCESS");
            idempotencyService.storeResponse("payment:refund", idempotencyKey,
                objectMapper.writeValueAsString(response));

            log.info("Refund processed successfully: refundId={}, tradeId={}, amount={}", 
                refundId, tradeId, refundAmountCents);

        } catch (Exception e) {
            log.error("Refund processing failed: refundId={}, tradeId={}", 
                refundId, tradeId, e);
            throw e;
        }
    }
}
