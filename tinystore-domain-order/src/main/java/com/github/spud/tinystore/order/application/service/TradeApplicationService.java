package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.CancelTradeCommand;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.domain.enums.InventoryStatus;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.enums.PromotionStatus;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.model.OrderLine;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.dto.*;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 交易应用服务（Saga 核心逻辑）
 */
@Slf4j
@Service
public class TradeApplicationService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ShopOrderRepository shopOrderRepository;

    @Autowired
    private PaymentIntentJpaRepository paymentIntentJpaRepository;

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
     * 创建交易 Saga（半编排式）
     * 步骤：
     * 1. 检查幂等性
     * 2. 调用 promotion quote
     * 3. 调用 inventory pre-occupy
     * 4. 生成 Trade/ShopOrder/OrderLine
     * 5. 生成 PaymentIntent
     * 6. 写入 Outbox 事件
     * 7. 缓存幂等响应
     *
     * @param idempotencyKey 幂等键
     * @param command 创建交易命令
     * @return 返回 tradeId, payableAmountCents, paymentIntentId 等
     */
    @Transactional
    public Map<String, Object> createTrade(String idempotencyKey, CreateTradeCommand command) throws Exception {
        try {
            // 1. 幂等性检查与获取缓存
            String fingerprint = command.getTradeId() + ":" + command.getBuyerId();
            if (!idempotencyService.tryAcquire("trade:create", idempotencyKey, fingerprint)) {
                String cachedResponse = idempotencyService.getCachedResponse("trade:create", idempotencyKey);
                if (cachedResponse != null) {
                    log.info("Idempotent trade creation: returning cached response");
                    return objectMapper.readValue(cachedResponse, Map.class);
                }
                throw new DomainConflictException("IDEMPOTENT_CONFLICT",
                    "Trade creation already in progress with this idempotency key");
            }

            // 2. 调用 promotion quote（优惠报价）
            PromotionQuoteRequest quoteRequest = buildPromotionQuoteRequest(command);
            PromotionQuoteResponse quoteResponse;
            try {
                quoteResponse = promotionClient.quote(idempotencyKey, quoteRequest);
                log.info("Promotion quote succeeded: discountAmountCents={}", quoteResponse.getDiscountAmountCents());
            } catch (Exception e) {
                log.error("Promotion quote failed", e);
                throw new DomainConflictException("PROMOTION_QUOTE_FAILED", "Failed to get promotion quote: " + e.getMessage());
            }

            // 2.1 校验 promotion quote 响应关键字段
            if (quoteResponse.getQuoteId() == null || quoteResponse.getQuoteId().isEmpty()) {
                throw new DomainConflictException("PROMOTION_QUOTE_INVALID", 
                    "Promotion quote response missing quoteId for tradeId: " + command.getTradeId());
            }
            if (quoteResponse.getInputHash() == null || quoteResponse.getInputHash().isEmpty()) {
                throw new DomainConflictException("PROMOTION_QUOTE_INVALID", 
                    "Promotion quote response missing inputHash for tradeId: " + command.getTradeId());
            }

            // 2.5. 创建 Trade（写入 quoteId/inputHash/couponCode）
            long totalAmountCents = quoteRequest.getItemsTotalCents();
            long discountAmountCents = quoteResponse.getDiscountAmountCents();
            long payableAmountCents = totalAmountCents - discountAmountCents;
            
            Trade trade = Trade.builder()
                .tradeId(command.getTradeId())
                .buyerId(command.getBuyerId())
                .buyerNick(command.getBuyerNick())
                .payStatus(PayStatus.UNPAID)
                .totalAmountCents(totalAmountCents)
                .discountAmountCents(discountAmountCents)
                .payableAmountCents(payableAmountCents)
                .promotionQuoteId(quoteResponse.getQuoteId())
                .promotionInputHash(quoteResponse.getInputHash())
                .couponCode(command.getCouponCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            trade = tradeRepository.save(trade);
            log.info("Trade created with promotionQuoteId: {}", quoteResponse.getQuoteId());

            // 3. 调用 inventory pre-occupy（库存预占）
            InventoryPreOccupyRequest preOccupyRequest = buildInventoryPreOccupyRequest(command);
            InventoryPreOccupyResponse preOccupyResponse;
            try {
                preOccupyResponse = inventoryClient.preOccupy(idempotencyKey, preOccupyRequest);
                if (preOccupyResponse == null || !preOccupyResponse.getSuccess()) {
                    throw new DomainConflictException("INVENTORY_PREOCCUPY_FAILED", "Inventory pre-occupy failed");
                }
                log.info("Inventory pre-occupy succeeded: reservationId={}", preOccupyResponse.getReservationId());
            } catch (Exception e) {
                log.error("Inventory pre-occupy failed", e);
                // 调用 promotion release 补偿
                try {
                    promotionClient.release(idempotencyKey, PromotionReleaseRequest.builder()
                        .quoteId(trade.getPromotionQuoteId())
                        .orderNo(trade.getTradeId())
                        .reason("INVENTORY_PREOCCUPY_FAILED")
                        .build());
                } catch (Exception releaseE) {
                    log.error("Promotion release failed during compensation", releaseE);
                }
                throw new DomainConflictException("INVENTORY_PREOCCUPY_FAILED", "Inventory pre-occupy failed: " + e.getMessage());
            }

            // 3.5. 更新 Trade，写入 inventory reservation ID
            trade = Trade.builder()
                .id(trade.getId())
                .tradeId(trade.getTradeId())
                .buyerId(trade.getBuyerId())
                .buyerNick(trade.getBuyerNick())
                .payStatus(trade.getPayStatus())
                .totalAmountCents(trade.getTotalAmountCents())
                .discountAmountCents(trade.getDiscountAmountCents())
                .payableAmountCents(trade.getPayableAmountCents())
                .promotionQuoteId(trade.getPromotionQuoteId())
                .promotionInputHash(trade.getPromotionInputHash())
                .couponCode(trade.getCouponCode())
                .inventoryReservationId(preOccupyResponse.getReservationId())
                .createdAt(trade.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
            trade = tradeRepository.save(trade);
            log.info("Trade updated with inventoryReservationId: {}", preOccupyResponse.getReservationId());

            // 4. promotion commit（现在 trade 已落库且有 inventoryReservationId，可以 commit 了）
            PromotionCommitRequest promotionCommitRequest = PromotionCommitRequest.builder()
                .quoteId(trade.getPromotionQuoteId())
                .orderNo(trade.getTradeId())
                .inputHash(trade.getPromotionInputHash())
                .build();
            try {
                promotionClient.commit(idempotencyKey, promotionCommitRequest);
                log.info("Promotion commit succeeded");
            } catch (Exception e) {
                log.error("Promotion commit failed", e);
                // promotion commit 失败不阻断流程，允许后续重试
            }

            // 5. 按 shopId/sellerId 分组生成 ShopOrder（拆店铺单）
            Map<String, List<CreateTradeCommand.OrderLineCommand>> groupedByShop = command.getOrderLines()
                .stream()
                .collect(Collectors.groupingBy(CreateTradeCommand.OrderLineCommand::getShopId));

            List<ShopOrder> shopOrders = new ArrayList<>();
            for (Map.Entry<String, List<CreateTradeCommand.OrderLineCommand>> entry : groupedByShop.entrySet()) {
                String shopId = entry.getKey();
                List<CreateTradeCommand.OrderLineCommand> lines = entry.getValue();

                String orderId = UUID.randomUUID().toString();
                String sellerId = lines.get(0).getSellerId(); // 同一 shopId 的 sellerId 相同

                // 创建订单行（领域值对象）
                List<OrderLine> orderLines = new ArrayList<>();
                for (CreateTradeCommand.OrderLineCommand line : lines) {
                    OrderLine orderLine = OrderLine.builder()
                        .skuId(line.getSkuId())
                        .productId(line.getProductId())
                        .productName(line.getProductName())
                        .quantity(line.getQuantity())
                        .priceCents(line.getPriceCents())
                        .lineAmountCents(line.getPriceCents() * line.getQuantity())
                        .build();
                    orderLines.add(orderLine);
                }

                ShopOrder shopOrder = ShopOrder.builder()
                    .orderId(orderId)
                    .tradeId(trade.getTradeId())
                    .shopId(shopId)
                    .sellerId(sellerId)
                    .orderStatus(OrderStatus.PENDING_PAY)
                    .inventoryStatus(InventoryStatus.LOCKED.getCode())
                    .promotionStatus(PromotionStatus.RESERVED.getCode())
                    .orderLines(orderLines)
                    .createdAt(LocalDateTime.now())
                    .build();
                shopOrder = shopOrderRepository.save(shopOrder);
                shopOrders.add(shopOrder);

                log.info("ShopOrder created: orderId={}, shopId={}, sellerId={}", orderId, shopId, sellerId);
            }

            // 5. 生成 PaymentIntent
            String paymentId = UUID.randomUUID().toString();
            
            // 计算支付超时时间（默认15分钟）
            LocalDateTime expireAt = LocalDateTime.now().plusSeconds(900);
            
            PaymentIntentEntity paymentIntent = PaymentIntentEntity.builder()
                .paymentId(paymentId)
                .tradeId(trade.getTradeId())
                .amountCents(trade.getPayableAmountCents())
                .buyerId(trade.getBuyerId())  // 新增：买家ID
                .payChannel("DEFAULT")  // 新增：默认支付渠道（可由前端传入）
                .expireAt(expireAt)  // 新增：支付超时时间
                .status("CREATED")
                .createdAt(LocalDateTime.now())
                .build();
            paymentIntent = paymentIntentJpaRepository.save(paymentIntent);
            log.info("PaymentIntent created: paymentId={}", paymentId);

            // 6. 写入 Outbox 事件（与业务数据同事务）
            // Trade 创建事件
            OrderDomainEvent tradeCreatedEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.TRADE_CREATED)
                .aggregateType("TRADE")
                .aggregateId(trade.getTradeId())
                .occurredAt(LocalDateTime.now())
                .traceId(command.getTraceId())
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "tradeId", trade.getTradeId(),
                    "buyerId", trade.getBuyerId(),
                    "payableAmountCents", trade.getPayableAmountCents()
                )))
                .build();
            outboxEventService.saveEvent(tradeCreatedEvent);

            // 子单创建事件
            for (ShopOrder shopOrder : shopOrders) {
                OrderDomainEvent orderCreatedEvent = OrderDomainEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(OrderEventType.ORDER_CREATED)
                    .aggregateType("ORDER")
                    .aggregateId(shopOrder.getOrderId())
                    .occurredAt(LocalDateTime.now())
                    .traceId(command.getTraceId())
                    .payloadJson(objectMapper.writeValueAsString(Map.of(
                        "orderId", shopOrder.getOrderId(),
                        "tradeId", shopOrder.getTradeId(),
                        "sellerId", shopOrder.getSellerId()
                    )))
                    .build();
                outboxEventService.saveEvent(orderCreatedEvent);
            }

            // 支付意图事件
            OrderDomainEvent paymentIntentEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.PAYMENT_INTENT_CREATED)
                .aggregateType("TRADE")
                .aggregateId(trade.getTradeId())
                .occurredAt(LocalDateTime.now())
                .traceId(command.getTraceId())
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "paymentId", paymentId,  // 保留旧字段名兼容
                    "paymentIntentId", paymentId,  // 新增：明确语义
                    "tradeId", trade.getTradeId(),
                    "amountCents", trade.getPayableAmountCents(),
                    "buyerId", trade.getBuyerId(),  // 新增：买家ID
                    "payChannel", paymentIntent.getPayChannel(),  // 新增：支付渠道
                    "expireAt", expireAt.toString()  // 新增：支付超时时间
                )))
                .build();
            outboxEventService.saveEvent(paymentIntentEvent);

            // 7. 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("tradeId", trade.getTradeId());
            result.put("payableAmountCents", trade.getPayableAmountCents());
            result.put("paymentIntentId", paymentId);
            result.put("reservationId", preOccupyResponse.getReservationId());

            // 缓存幂等响应
            idempotencyService.storeResponse("trade:create", idempotencyKey, 
                objectMapper.writeValueAsString(result));

            log.info("Trade creation succeeded: tradeId={}, paymentId={}", trade.getTradeId(), paymentId);
            return result;

        } catch (Exception e) {
            log.error("Trade creation failed", e);
            throw e;
        }
    }

    /**
     * 取消交易 Saga（仅未支付订单可取消）
     *
     * @param idempotencyKey 幂等键
     * @param command 取消交易命令
     */
    @Transactional
    public void cancelTrade(String idempotencyKey, CancelTradeCommand command) throws Exception {
        try {
            // 获取 Trade 聚合根
            Trade trade = tradeRepository.findByTradeId(command.getTradeId())
                .orElseThrow(() -> new DomainConflictException("TRADE_NOT_FOUND", 
                    "Trade not found: " + command.getTradeId()));

            // 检查是否可取消（业务规则在聚合根内）
            if (!trade.canCancel()) {
                throw new DomainConflictException("INVALID_STATE_TRANSITION",
                    "Trade cannot be cancelled in current status: " + trade.getPayStatus());
            }

            // 获取所有子单
            List<ShopOrder> shopOrders = shopOrderRepository.findByTradeId(trade.getTradeId());

            // 更新 Trade 状态（注意：Trade 没有 close() 方法，这里需要添加或直接设置）
            // 暂时保持直接设置，后续可优化为 trade.cancel()
            trade = Trade.builder()
                .tradeId(trade.getTradeId())
                .buyerId(trade.getBuyerId())
                .buyerNick(trade.getBuyerNick())
                .payStatus(PayStatus.UNPAID)
                .totalAmountCents(trade.getTotalAmountCents())
                .discountAmountCents(trade.getDiscountAmountCents())
                .payableAmountCents(trade.getPayableAmountCents())
                .createdAt(trade.getCreatedAt())
                .closedAt(LocalDateTime.now())
                .build();
            tradeRepository.save(trade);

            // 更新所有子单状态
            for (ShopOrder shopOrder : shopOrders) {
                shopOrder.close();
                shopOrderRepository.save(shopOrder);
            }

            // 写入 Outbox 事件
            OrderDomainEvent tradeClosedEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.TRADE_CLOSED)
                .aggregateType("TRADE")
                .aggregateId(trade.getTradeId())
                .occurredAt(LocalDateTime.now())
                .traceId(command.getTraceId())
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "tradeId", trade.getTradeId(),
                    "reason", command.getReason()
                )))
                .build();
            outboxEventService.saveEvent(tradeClosedEvent);

            for (ShopOrder shopOrder : shopOrders) {
                OrderDomainEvent orderClosedEvent = OrderDomainEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(OrderEventType.ORDER_CLOSED)
                    .aggregateType("ORDER")
                    .aggregateId(shopOrder.getOrderId())
                    .occurredAt(LocalDateTime.now())
                    .traceId(command.getTraceId())
                    .payloadJson(objectMapper.writeValueAsString(Map.of(
                        "orderId", shopOrder.getOrderId(),
                        "tradeId", shopOrder.getTradeId()
                    )))
                    .build();
                outboxEventService.saveEvent(orderClosedEvent);
            }

            // 同步补偿：释放库存和优惠
            try {
                // 校验 inventoryReservationId 已绑定
                if (trade.getInventoryReservationId() == null || trade.getInventoryReservationId().isEmpty()) {
                    log.warn("Missing inventoryReservationId for trade: {}, skipping inventory release", trade.getTradeId());
                } else {
                    InventoryReleaseRequest inventoryReleaseRequest = InventoryReleaseRequest.builder()
                        .reservationId(trade.getInventoryReservationId())
                        .build();
                    inventoryClient.release(idempotencyKey, inventoryReleaseRequest);
                    log.info("Inventory released for cancelled trade: tradeId={}", trade.getTradeId());
                }
            } catch (Exception e) {
                log.error("Failed to release inventory for cancelled trade: tradeId={}", trade.getTradeId(), e);
            }

            try {
                // 校验 promotionQuoteId 已绑定
                if (trade.getPromotionQuoteId() == null || trade.getPromotionQuoteId().isEmpty()) {
                    log.warn("Missing promotionQuoteId for trade: {}, skipping promotion release", trade.getTradeId());
                } else {
                    PromotionReleaseRequest promotionReleaseRequest = PromotionReleaseRequest.builder()
                        .quoteId(trade.getPromotionQuoteId())
                        .orderNo(trade.getTradeId())
                        .reason(command.getReason())
                        .build();
                    promotionClient.release(idempotencyKey, promotionReleaseRequest);
                    log.info("Promotion released for cancelled trade: tradeId={}", trade.getTradeId());
                }
            } catch (Exception e) {
                log.error("Failed to release promotion for cancelled trade: tradeId={}", trade.getTradeId(), e);
            }

            log.info("Trade cancelled: tradeId={}", trade.getTradeId());

        } catch (Exception e) {
            log.error("Trade cancellation failed", e);
            throw e;
        }
    }

    /**
     * 支付成功回写 Saga
     *
     * @param idempotencyKey 幂等键
     * @param command 支付成功命令
     */
    @Transactional
    public void onPaymentSucceeded(String idempotencyKey, PaymentSucceededCommand command) throws Exception {
        try {
            // 检查 PaymentIntent 幂等性（以 paymentId 为唯一键）
            PaymentIntentEntity paymentIntent = paymentIntentJpaRepository.findByPaymentId(command.getPaymentId())
                .orElseThrow(() -> new DomainConflictException("PAYMENT_INTENT_NOT_FOUND",
                    "Payment intent not found: " + command.getPaymentId()));

            if ("PAID".equals(paymentIntent.getStatus())) {
                log.info("Payment already processed (idempotent): paymentId={}", command.getPaymentId());
                return;
            }

            // 获取 Trade 聚合根
            Trade trade = tradeRepository.findByTradeId(command.getTradeId())
                .orElseThrow(() -> new DomainConflictException("TRADE_NOT_FOUND",
                    "Trade not found: " + command.getTradeId()));

            // 检查金额一致性
            if (!trade.getPayableAmountCents().equals(command.getPaidAmountCents())) {
                throw new DomainConflictException("PAYMENT_AMOUNT_MISMATCH",
                    "Payment amount mismatch: expected " + trade.getPayableAmountCents() + 
                    ", got " + command.getPaidAmountCents());
            }

            // 更新 Trade 支付状态（使用聚合根方法）
            trade.markAsPaid();
            tradeRepository.save(trade);

            // 获取所有子单，更新履约状态
            List<ShopOrder> shopOrders = shopOrderRepository.findByTradeId(trade.getTradeId());
            for (ShopOrder shopOrder : shopOrders) {
                // 更新为待发货（ShopOrder 需要添加此方法或直接重建）
                ShopOrder updated = ShopOrder.builder()
                    .orderId(shopOrder.getOrderId())
                    .tradeId(shopOrder.getTradeId())
                    .shopId(shopOrder.getShopId())
                    .sellerId(shopOrder.getSellerId())
                    .orderStatus(OrderStatus.PENDING_SHIP)
                    .inventoryStatus(shopOrder.getInventoryStatus())
                    .promotionStatus(shopOrder.getPromotionStatus())
                    .orderLines(shopOrder.getOrderLines())
                    .createdAt(shopOrder.getCreatedAt())
                    .build();
                shopOrderRepository.save(updated);
            }

            // 更新 PaymentIntent 状态
            paymentIntent.setStatus("PAID");
            paymentIntent.setPaidAt(LocalDateTime.now());
            paymentIntentJpaRepository.save(paymentIntent);

            // 同步确认：inventory commit 和 promotion commit
            try {
                InventoryCommitRequest inventoryCommitRequest = InventoryCommitRequest.builder()
                    .orderId(trade.getTradeId())
                    .build();
                inventoryClient.commit(idempotencyKey, inventoryCommitRequest);
                log.info("Inventory committed for paid trade: tradeId={}", trade.getTradeId());
            } catch (Exception e) {
                log.error("Failed to commit inventory for paid trade: tradeId={}", trade.getTradeId(), e);
                // 不抛异常，允许重试
            }

            try {
                // 校验 promotionQuoteId/inputHash 已绑定
                if (trade.getPromotionQuoteId() == null || trade.getPromotionQuoteId().isEmpty()) {
                    log.warn("Missing promotionQuoteId for trade: {}, skipping promotion commit", trade.getTradeId());
                } else if (trade.getPromotionInputHash() == null || trade.getPromotionInputHash().isEmpty()) {
                    log.warn("Missing promotionInputHash for trade: {}, skipping promotion commit", trade.getTradeId());
                } else {
                    PromotionCommitRequest promotionCommitRequest = PromotionCommitRequest.builder()
                        .quoteId(trade.getPromotionQuoteId())
                        .orderNo(trade.getTradeId())
                        .inputHash(trade.getPromotionInputHash())
                        .payNo(command.getPaymentId())
                        .paidAt(System.currentTimeMillis())
                        .build();
                    promotionClient.commit(idempotencyKey, promotionCommitRequest);
                    log.info("Promotion committed for paid trade: tradeId={}", trade.getTradeId());
                }
            } catch (Exception e) {
                log.error("Failed to commit promotion for paid trade: tradeId={}", trade.getTradeId(), e);
                // 不抛异常，允许重试
            }

            // 写入 Outbox 事件
            OrderDomainEvent tradePaidEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.TRADE_PAID)
                .aggregateType("TRADE")
                .aggregateId(trade.getTradeId())
                .occurredAt(LocalDateTime.now())
                .traceId(command.getTraceId())
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                    "tradeId", trade.getTradeId(),
                    "paymentId", command.getPaymentId(),
                    "paidAmountCents", command.getPaidAmountCents()
                )))
                .build();
            outboxEventService.saveEvent(tradePaidEvent);

            for (ShopOrder shopOrder : shopOrders) {
                OrderDomainEvent orderPaidEvent = OrderDomainEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(OrderEventType.ORDER_PAID)
                    .aggregateType("ORDER")
                    .aggregateId(shopOrder.getOrderId())
                    .occurredAt(LocalDateTime.now())
                    .traceId(command.getTraceId())
                    .payloadJson(objectMapper.writeValueAsString(Map.of(
                        "orderId", shopOrder.getOrderId(),
                        "tradeId", shopOrder.getTradeId()
                    )))
                    .build();
                outboxEventService.saveEvent(orderPaidEvent);
            }

            log.info("Payment succeeded callback processed: paymentId={}, tradeId={}", 
                command.getPaymentId(), trade.getTradeId());

        } catch (Exception e) {
            log.error("Payment success callback failed", e);
            throw e;
        }
    }

    /**
     * 确认交易收货（买家侧触发）
     * 逻辑：找到该 trade 下所有订单，调用确认收货
     *
     * @param tradeId 交易ID
     * @param traceId 追踪ID
     */
    @Transactional
    public void confirmTradeReceipt(String tradeId, String traceId) throws Exception {
        try {
            Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new DomainConflictException("TRADE_NOT_FOUND", "Trade not found: " + tradeId));

            // 查找该交易下的所有店铺订单
            List<ShopOrder> shopOrders = shopOrderRepository.findByTradeId(tradeId);
            if (shopOrders.isEmpty()) {
                log.warn("No shop orders found for trade: tradeId={}", tradeId);
                return;
            }

            // 逐个订单确认收货（使用聚合根方法）
            for (ShopOrder shopOrder : shopOrders) {
                if (OrderStatus.PENDING_RECEIVE.equals(shopOrder.getOrderStatus())) {
                    shopOrder.markAsSuccess();
                    shopOrderRepository.save(shopOrder);

                    // 发布订单完成事件
                    OrderDomainEvent orderSuccessEvent = OrderDomainEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(OrderEventType.ORDER_SUCCESS)
                        .aggregateType("ORDER")
                        .aggregateId(shopOrder.getOrderId())
                        .occurredAt(LocalDateTime.now())
                        .traceId(traceId)
                        .payloadJson(objectMapper.writeValueAsString(Map.of(
                            "orderId", shopOrder.getOrderId(),
                            "tradeId", tradeId
                        )))
                        .build();
                    outboxEventService.saveEvent(orderSuccessEvent);

                    log.info("Shop order receipt confirmed: orderId={}", shopOrder.getOrderId());
                }
            }

            log.info("Trade receipt confirmed: tradeId={}", tradeId);

        } catch (Exception e) {
            log.error("Confirm trade receipt failed: tradeId={}", tradeId, e);
            throw e;
        }
    }

    // ============ 辅助方法 ============

    private PromotionQuoteRequest buildPromotionQuoteRequest(CreateTradeCommand command) {
        Long itemsTotalCents = command.getOrderLines().stream()
            .mapToLong(line -> line.getPriceCents() * line.getQuantity())
            .sum();

        List<PromotionQuoteRequest.LineItem> lines = command.getOrderLines().stream()
            .map(line -> new PromotionQuoteRequest.LineItem(line.getSkuId(), line.getQuantity(), line.getPriceCents()))
            .collect(Collectors.toList());

        return PromotionQuoteRequest.builder()
            .buyerId(command.getBuyerId())
            .shopId(command.getOrderLines().get(0).getShopId()) // 先取第一个，暂不处理多商家 quote
            .itemsTotalCents(itemsTotalCents)
            .lines(lines)
            .couponCode(command.getCouponCode())
            .build();
    }

    private InventoryPreOccupyRequest buildInventoryPreOccupyRequest(CreateTradeCommand command) {
        List<InventoryPreOccupyRequest.LineItem> lines = command.getOrderLines().stream()
            .map(line -> new InventoryPreOccupyRequest.LineItem(line.getSkuId(), line.getQuantity()))
            .collect(Collectors.toList());

        return InventoryPreOccupyRequest.builder()
            .orderId(UUID.randomUUID().toString()) // 这里使用临时 ID，后续会对应真实订单
            .lines(lines)
            .build();
    }
}
