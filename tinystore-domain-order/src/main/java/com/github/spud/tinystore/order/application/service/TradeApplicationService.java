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
import com.github.spud.tinystore.order.domain.model.InventoryOccupyPair;
import com.github.spud.tinystore.order.domain.model.OrderLine;
import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.domain.service.TradeIdGenerator;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.dto.*;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.IdempotencyService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.PaymentIntentEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PaymentIntentJpaRepository;
import com.github.spud.tinystore.order.interfaces.dto.response.CreateTradeData;
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
     * @param command        创建交易命令
     * @return 返回 CreateTradeData（tradeId, payableAmountCents, paymentIntentId）
     */
    @Transactional
    public CreateTradeData createTrade(String idempotencyKey, CreateTradeCommand command) throws Exception {
        try {
            String tradeId = (command.getTradeId() != null && !command.getTradeId().isEmpty())
                    ? command.getTradeId()
                    : TradeIdGenerator.generateTradeId();

            // 1. 幂等性检查与获取缓存
            String fingerprint = tradeId + ":" + command.getBuyerId();
            if (!idempotencyService.tryAcquire("trade:create", idempotencyKey, fingerprint)) {
                String cachedResponse = idempotencyService.getCachedResponse("trade:create", idempotencyKey);
                if (cachedResponse != null) {
                    log.info("Idempotent trade creation: returning cached response");
                    return objectMapper.readValue(cachedResponse, CreateTradeData.class);
                }
                throw new DomainConflictException("IDEMPOTENT_CONFLICT",
                        "Trade creation already in progress with this idempotency key");
            }

            // 2. 调用 promotion quote（优惠报价）
            PromotionQuoteRequest quoteRequest = buildPromotionQuoteRequest(command);
            PromotionQuoteResponse quoteResponse;
            try {
                quoteResponse = promotionClient.quote(idempotencyKey, quoteRequest);
                log.info("Promotion quote succeeded: status={}, quoteId={}",
                        quoteResponse.getStatus(), quoteResponse.getQuoteId());
            } catch (Exception e) {
                log.error("Promotion quote failed", e);
                throw new DomainConflictException("PROMOTION_QUOTE_FAILED", "Failed to get promotion quote: " + e.getMessage());
            }

            // 2.1 校验 promotion quote 响应关键字段
            if (quoteResponse == null || quoteResponse.getSnapshot() == null) {
                throw new DomainConflictException("PROMOTION_QUOTE_INVALID",
                        "Promotion quote response is null or missing snapshot for tradeId: " + command.getTradeId());
            }
            if (quoteResponse.getQuoteId() == null || quoteResponse.getQuoteId().isEmpty()) {
                throw new DomainConflictException("PROMOTION_QUOTE_INVALID",
                        "Promotion quote response missing quoteId for tradeId: " + command.getTradeId());
            }
            PromotionQuoteResponse.PricingSnapshot snapshot = quoteResponse.getSnapshot();
            if (snapshot.getVersion() == null || snapshot.getVersion().getInputHash() == null) {
                throw new DomainConflictException("PROMOTION_QUOTE_INVALID",
                        "Promotion quote response missing snapshot.version.inputHash for tradeId: " + command.getTradeId());
            }

            // 2.2 处理 quote status
            String inputHash = snapshot.getVersion().getInputHash();
            if (quoteResponse.getStatus() == PromotionQuoteResponse.CheckoutResultStatus.REQUOTE_REQUIRED) {
                log.warn("Promotion quote requires re-quote: tradeId={}, changeReasons={}",
                        command.getTradeId(), quoteResponse.getChangeReasons());
                throw new DomainConflictException("PROMOTION_REQUOTE_REQUIRED",
                        "Promotion quote requires re-quote due to changes: " + quoteResponse.getChangeReasons());
            }
            if (quoteResponse.getStatus() == PromotionQuoteResponse.CheckoutResultStatus.OK_WITH_CHANGE) {
                log.warn("Promotion quote changed: tradeId={}, changeReasons={}",
                        command.getTradeId(), quoteResponse.getChangeReasons());
                // 允许继续，但记录 changeReasons 供后续审计
            }

            // 2.3 从 snapshot 提取金额
            long totalAmountCents = snapshot.getItemsTotalCents() != null ? snapshot.getItemsTotalCents() : 0L;
            long promotionDiscountCents = snapshot.getPromotionDiscountTotalCents() != null ? snapshot.getPromotionDiscountTotalCents() : 0L;
            long couponDiscountCents = snapshot.getCouponDiscountTotalCents() != null ? snapshot.getCouponDiscountTotalCents() : 0L;
            long discountAmountCents = promotionDiscountCents + couponDiscountCents;
            long payableAmountCents = snapshot.getPayableCents() != null ? snapshot.getPayableCents() : 0L;

            // 2.5. 创建 Trade（写入 quoteId/inputHash/couponCode + couponCodes）
            // 构建 couponCodes 列表（合并平台券+店铺券）
            List<String> allCouponCodes = new ArrayList<>();
            if (command.getPlatformCouponCodes() != null) {
                allCouponCodes.addAll(command.getPlatformCouponCodes());
            }
            if (command.getShopCouponCodesByShop() != null) {
                command.getShopCouponCodesByShop().values().forEach(allCouponCodes::addAll);
            }

            // 3. 按 shopId 分组生成 ShopOrder（先生成，后续会补充 inventoryOccupyPairs）
            Map<String, List<CreateTradeCommand.OrderLineCommand>> groupedByShop = command.getOrderLines()
                    .stream()
                    .collect(Collectors.groupingBy(CreateTradeCommand.OrderLineCommand::getShopId));

            List<ShopOrder> shopOrders = new ArrayList<>();
            for (Map.Entry<String, List<CreateTradeCommand.OrderLineCommand>> entry : groupedByShop.entrySet()) {
                String shopId = entry.getKey();
                List<CreateTradeCommand.OrderLineCommand> lines = entry.getValue();

                String orderId = TradeIdGenerator.generateOrderId();
                String sellerId = lines.get(0).getSellerId(); // 同一 shopId 的 sellerId 相同

                // 创建订单行（领域值对象）
                List<OrderLine> orderLines = new ArrayList<>();
                for (CreateTradeCommand.OrderLineCommand line : lines) {
                    OrderLine orderLine = line.toOrderLine();
                    orderLines.add(orderLine);
                }

                ShopOrder shopOrder = ShopOrder.builder()
                        .orderId(orderId)
                        .tradeId(tradeId)
                        .shopId(shopId)
                        .sellerId(sellerId)
                        .orderStatus(OrderStatus.PENDING_PAY)
                        .inventoryStatus(InventoryStatus.LOCKED.getCode())
                        .promotionStatus(PromotionStatus.RESERVED.getCode())
                        .orderLines(orderLines)
                        .createdAt(LocalDateTime.now())
                        .build();
                shopOrders.add(shopOrder);
                log.info("ShopOrder created: orderId={}, shopId={}, sellerId={}", orderId, shopId, sellerId);
            }

            // 4. 调用 inventory deduct（V2：按 shop 分组调用，Redis 原子扣减）
            List<ShopOrder> updatedOrders = new ArrayList<>();
            for (ShopOrder shopOrder : shopOrders) {
                String shopId = shopOrder.getShopId();
                List<CreateTradeCommand.OrderLineCommand> lines = groupedByShop.get(shopId);

                // 构建该 shop 的 deduct 请求
                List<InventoryDeductRequest.Item> deductItems = lines.stream()
                        .map(line -> InventoryDeductRequest.Item.builder()
                                .shopId(shopId)
                                .skuId(line.getSkuId())
                                .quantity(line.getQuantity())
                                .build())
                        .collect(Collectors.toList());

                InventoryDeductRequest deductRequest = InventoryDeductRequest.builder()
                        .orderId(shopOrder.getOrderId())
                        .items(deductItems)
                        .build();

                // 为每个 shop 派生独立幂等键
                String shopIdempotencyKey = idempotencyKey + ":inv:deduct:" + shopId;
                InventoryDeductResponse deductResponse;
                try {
                    deductResponse = inventoryClient.deduct(shopIdempotencyKey, deductRequest);
                    if (deductResponse == null || !Boolean.TRUE.equals(deductResponse.getSuccess())) {
                        String msg = deductResponse != null ? deductResponse.getMessage() : "null response";
                        throw new DomainConflictException("INVENTORY_DEDUCT_FAILED",
                                "Inventory deduct failed for shop: " + shopId + ", msg: " + msg);
                    }
                    log.info("Inventory deduct succeeded for shop {}: occupyPairs={}",
                            shopId, deductResponse.getOccupyPairs());
                } catch (Exception e) {
                    log.error("Inventory deduct failed for shop: {}", shopId, e);
                    // 补偿：释放已成功扣减的其他 shop
                    for (ShopOrder prev : updatedOrders) {
                        try {
                            List<InventoryReleaseRequestV2.OccupyPairDto> relPairs = prev.getInventoryOccupyPairs().stream()
                                    .map(p -> InventoryReleaseRequestV2.OccupyPairDto.builder()
                                            .shopId(p.getShopId()).skuId(p.getSkuId()).occupyId(p.getOccupyId()).build())
                                    .collect(Collectors.toList());
                            inventoryClient.releaseV2(idempotencyKey + ":inv:comp:" + prev.getShopId(),
                                    InventoryReleaseRequestV2.builder()
                                            .orderId(prev.getOrderId())
                                            .reason("INVENTORY_DEDUCT_COMPENSATION")
                                            .occupyPairs(relPairs)
                                            .build());
                        } catch (Exception compE) {
                            log.error("Compensation release failed for shop: {}", prev.getShopId(), compE);
                        }
                    }
                    // 调用 promotion release 补偿
                    try {
                        promotionClient.release(idempotencyKey, PromotionReleaseRequest.builder()
                                .quoteId(quoteResponse.getQuoteId())
                                .tradeId(tradeId)
                                .reason("INVENTORY_DEDUCT_FAILED")
                                .build());
                    } catch (Exception releaseE) {
                        log.error("Promotion release failed during compensation", releaseE);
                    }
                    throw new DomainConflictException("INVENTORY_DEDUCT_FAILED",
                            "Inventory deduct failed for shop: " + shopId + ", error: " + e.getMessage());
                }

                // 转换 occupyPairs 为领域值对象
                List<InventoryOccupyPair> occupyPairs = deductResponse.getOccupyPairs().stream()
                        .map(dto -> new InventoryOccupyPair(dto.getShopId(), dto.getSkuId(), dto.getOccupyId()))
                        .collect(Collectors.toList());

                // 保存 occupyPairs 到 ShopOrder（使用 Builder 重建实例，保留所有原字段）
                ShopOrder updatedShopOrder = ShopOrder.builder()
                        .id(shopOrder.getId())
                        .orderId(shopOrder.getOrderId())
                        .tradeId(shopOrder.getTradeId())
                        .shopId(shopOrder.getShopId())
                        .sellerId(shopOrder.getSellerId())
                        .orderStatus(shopOrder.getOrderStatus())
                        .inventoryStatus(shopOrder.getInventoryStatus())
                        .promotionStatus(shopOrder.getPromotionStatus())
                        .totalAmountCents(shopOrder.getTotalAmountCents())
                        .orderLines(shopOrder.getOrderLines())
                        .inventoryOccupyPairs(occupyPairs)
                        .createdAt(shopOrder.getCreatedAt())
                        .updatedAt(shopOrder.getUpdatedAt())
                        .acceptedAt(shopOrder.getAcceptedAt())
                        .build();
                updatedOrders.add(updatedShopOrder);
                log.info("ShopOrder updated with occupyPairs: orderId={}, occupyPairs={}",
                        shopOrder.getOrderId(), occupyPairs);
            }

            // 5. promotion commit（现在 trade 已落库且所有 shop 库存已预占，可以 commit 了）
            PromotionCommitRequest promotionCommitRequest = PromotionCommitRequest.builder()
                    .quoteId(quoteResponse.getQuoteId())
                    .tradeId(tradeId)
                    .inputHash(inputHash)
                    .build();
            try {
                promotionClient.commit(idempotencyKey, promotionCommitRequest);
                log.info("Promotion commit succeeded");
            } catch (Exception e) {
                log.error("Promotion commit failed", e);
                throw new DomainConflictException("PROMOTION_COMMIT_FAILED",
                        "Promotion commit failed for tradeId: " + tradeId + ", error: " + e.getMessage());
                // promotion commit 失败不阻断流程，允许后续重试
            }

            Trade trade = Trade.builder()
                    .tradeId(tradeId)
                    .buyerId(command.getBuyerId())
                    .buyerNick(command.getBuyerNick())
                    .payStatus(PayStatus.UNPAID)
                    .totalAmountCents(totalAmountCents)
                    .discountAmountCents(discountAmountCents)
                    .payableAmountCents(payableAmountCents)
                    .promotionQuoteId(quoteResponse.getQuoteId())
                    .promotionInputHash(inputHash)
                    .couponCodes(allCouponCodes)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            trade = tradeRepository.save(trade);
            Boolean saved = shopOrderRepository.saveAll(updatedOrders);
            if (!saved) {
                //TODO: 这里需要补偿：调用 inventory release 和 promotion release
            }
            log.info("Trade created with promotionQuoteId: {}, inputHash: {}", quoteResponse.getQuoteId(), inputHash);

            // 6. 生成 PaymentIntent
            String paymentId = TradeIdGenerator.generatePaymentIntentId();

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
            CreateTradeData result = CreateTradeData.builder()
                    .tradeId(trade.getTradeId())
                    .payableAmountCents(trade.getPayableAmountCents())
                    .paymentIntentId(paymentId)
                    .build();

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
     * @param command        取消交易命令
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

            // 更新 Trade 状态（保留所有字段，仅设置 closedAt）
            trade.closeTrade();
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
            // 按 ShopOrder 遍历释放库存（仅 V2 occupyPairs）
            for (ShopOrder shopOrder : shopOrders) {
                try {
                    if (shopOrder.getInventoryOccupyPairs() != null && !shopOrder.getInventoryOccupyPairs().isEmpty()) {
                        // V2：使用 releaseV2 按 SKU 释放
                        List<InventoryReleaseRequestV2.OccupyPairDto> relPairs = shopOrder.getInventoryOccupyPairs().stream()
                                .map(p -> InventoryReleaseRequestV2.OccupyPairDto.builder()
                                        .shopId(p.getShopId()).skuId(p.getSkuId()).occupyId(p.getOccupyId()).build())
                                .collect(Collectors.toList());
                        InventoryReleaseRequestV2 releaseRequestV2 = InventoryReleaseRequestV2.builder()
                                .orderId(shopOrder.getOrderId())
                                .reason(command.getReason())
                                .occupyPairs(relPairs)
                                .build();
                        String shopIdempotencyKey = idempotencyKey + ":inv:rel:" + shopOrder.getShopId();
                        inventoryClient.releaseV2(shopIdempotencyKey, releaseRequestV2);
                        log.info("Inventory V2 released for cancelled shopOrder: orderId={}, shopId={}, occupyPairs={}",
                                shopOrder.getOrderId(), shopOrder.getShopId(), shopOrder.getInventoryOccupyPairs());
                    } else {
                        log.warn("Missing inventory occupy info for shopOrder: {}, skipping inventory release", shopOrder.getOrderId());
                    }
                } catch (Exception e) {
                    log.error("Failed to release inventory for shopOrder: {}", shopOrder.getOrderId(), e);
                    // 继续处理其他 shop
                }
            }

            // 释放促销优惠
            try {
                // 校验 promotionQuoteId 已绑定
                if (trade.getPromotionQuoteId() == null || trade.getPromotionQuoteId().isEmpty()) {
                    log.warn("Missing promotionQuoteId for trade: {}, skipping promotion release", trade.getTradeId());
                } else {
                    PromotionReleaseRequest promotionReleaseRequest = PromotionReleaseRequest.builder()
                            .quoteId(trade.getPromotionQuoteId())
                            .tradeId(trade.getTradeId())
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
     * @param command        支付成功命令
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
                        .id(shopOrder.getId())
                        .orderId(shopOrder.getOrderId())
                        .tradeId(shopOrder.getTradeId())
                        .shopId(shopOrder.getShopId())
                        .sellerId(shopOrder.getSellerId())
                        .orderStatus(OrderStatus.PENDING_SHIP)
                        .inventoryStatus(shopOrder.getInventoryStatus())
                        .promotionStatus(shopOrder.getPromotionStatus())
                        .inventoryOccupyPairs(shopOrder.getInventoryOccupyPairs())
                        .totalAmountCents(shopOrder.getTotalAmountCents())
                        .orderLines(shopOrder.getOrderLines())
                        .createdAt(shopOrder.getCreatedAt())
                        .updatedAt(shopOrder.getUpdatedAt())
                        .acceptedAt(shopOrder.getAcceptedAt())
                        .build();
                shopOrderRepository.save(updated);
            }

            // 更新 PaymentIntent 状态
            paymentIntent.setStatus("PAID");
            paymentIntent.setPaidAt(LocalDateTime.now());
            paymentIntentJpaRepository.save(paymentIntent);

            // 同步确认：V2 链路为原子扣减，无需 inventory commit
            long paidAtEpochMs = System.currentTimeMillis();
            for (ShopOrder shopOrder : shopOrders) {
                try {
                    log.info("V2 inventory deduct path, skip commit for shopOrder: orderId={}, shopId={}, occupyPairs={}",
                            shopOrder.getOrderId(), shopOrder.getShopId(),
                            shopOrder.getInventoryOccupyPairs() != null ? shopOrder.getInventoryOccupyPairs().size() : 0);
                } catch (Exception e) {
                    log.error("Failed to commit inventory for shopOrder: {}", shopOrder.getOrderId(), e);
                    // 继续处理其他 shop，不抛异常，允许重试
                }
            }

            // 同步确认：promotion commit
            try {
                // 校验 promotionQuoteId/inputHash 已绑定
                if (trade.getPromotionQuoteId() == null || trade.getPromotionQuoteId().isEmpty()) {
                    log.warn("Missing promotionQuoteId for trade: {}, skipping promotion commit", trade.getTradeId());
                } else if (trade.getPromotionInputHash() == null || trade.getPromotionInputHash().isEmpty()) {
                    log.warn("Missing promotionInputHash for trade: {}, skipping promotion commit", trade.getTradeId());
                } else {
                    PromotionCommitRequest promotionCommitRequest = PromotionCommitRequest.builder()
                            .quoteId(trade.getPromotionQuoteId())
                            .tradeId(trade.getTradeId())
                            .inputHash(trade.getPromotionInputHash())
                            .payNo(command.getPaymentId())
                            .paidAt(paidAtEpochMs)
                            .build();
                    PromotionCommitResponse promotionCommitResponse = promotionClient.commit(idempotencyKey, promotionCommitRequest);

                    // 处理 promotion commit 响应状态
                    if (promotionCommitResponse != null && promotionCommitResponse.getStatus() == PromotionQuoteResponse.CheckoutResultStatus.REQUOTE_REQUIRED) {
                        log.error("Promotion commit requires re-quote after payment: tradeId={}, changeReasons={}",
                                trade.getTradeId(), promotionCommitResponse.getChangeReasons());
                        // 严重错误：支付后无法重新报价，记录错误但不阻断流程
                        // 实际生产中应触发人工介入或补偿流程
                    } else {
                        log.info("Promotion committed for paid trade: tradeId={}, status={}",
                                trade.getTradeId(), promotionCommitResponse != null ? promotionCommitResponse.getStatus() : "null");
                    }
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
        List<PromotionQuoteRequest.LineItem> lines = command.getOrderLines().stream()
                .map(line -> PromotionQuoteRequest.LineItem.builder()
                        .skuId(line.getSkuId())
                        .shopId(line.getShopId())
                        .quantity(line.getQuantity())
                        .baseUnitPriceCents(line.getPriceCents())
                        .weightGrams(line.getWeightGrams())
                        .build())
                .collect(Collectors.toList());

        // 构造 appliedIntent（支持多券：平台券列表 + 店铺券 Map）
        PromotionQuoteRequest.AppliedIntent appliedIntent = null;

        List<String> platformCodes = command.getPlatformCouponCodes();
        Map<String, List<String>> shopCodesMap = command.getShopCouponCodesByShop();

        // 若有券输入，构造 appliedIntent（现在使用 couponNo 而非 UUID）
        if ((platformCodes != null && !platformCodes.isEmpty()) ||
                (shopCodesMap != null && !shopCodesMap.isEmpty())) {
            appliedIntent = PromotionQuoteRequest.AppliedIntent.builder()
                    .platformCouponIds(platformCodes != null ? platformCodes : Collections.emptyList())
                    .shopCouponIdsByShop(shopCodesMap != null ? shopCodesMap : Collections.emptyMap())
                    .build();
        }

        return PromotionQuoteRequest.builder()
                .userId(command.getBuyerId())
                .addressId(command.getAddressId())
                .traceId(command.getTraceId())
                .lines(lines)
                .appliedIntent(appliedIntent)
                .build();
    }


}
