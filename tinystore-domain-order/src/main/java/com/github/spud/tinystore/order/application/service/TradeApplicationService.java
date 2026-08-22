package com.github.spud.tinystore.order.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.CancelTradeCommand;
import com.github.spud.tinystore.order.application.command.CreateTradeCommand;
import com.github.spud.tinystore.order.application.command.PaymentSucceededCommand;
import com.github.spud.tinystore.order.application.price.SkuPriceResolver;
import com.github.spud.tinystore.order.domain.enums.InventoryStatus;
import com.github.spud.tinystore.order.domain.enums.OrderStatus;
import com.github.spud.tinystore.order.domain.enums.PayStatus;
import com.github.spud.tinystore.order.domain.enums.PromotionStatus;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderEventType;
import com.github.spud.tinystore.order.domain.exception.DomainConflictException;
import com.github.spud.tinystore.order.domain.model.InventoryReservationRef;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private SkuPriceResolver skuPriceResolver;

    @Value("${order.pricing.authoritative-enabled:true}")
    private boolean priceAuthoritativeEnabled;

    @Value("${order.payment-timeout-seconds:900}")
    private long paymentTimeoutSeconds;

    @Value("${order.promotion.commit-async-enabled:false}")
    private boolean promotionCommitAsyncEnabled;

    @Value("${order.reservation.ttl-minutes:15}")
    private long reservationTtlMinutes;

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
        String tradeId = null;
        PromotionQuoteResponse quoteResponse = null;
        List<ShopOrder> updatedOrders = new ArrayList<>();
        boolean compensationRequired = false;
        try {
            tradeId = (command.getTradeId() != null && !command.getTradeId().isEmpty())
                    ? command.getTradeId()
                    : TradeIdGenerator.generateTradeId();

            // 1. 幂等性检查与获取缓存
            String fingerprint = tradeId + ":" + command.getBuyerId();
            if (!idempotencyService.tryAcquire("trade:create", idempotencyKey, fingerprint)) {
                String cachedResponse = idempotencyService.getCachedResponse("trade:create",
                        idempotencyKey);
                if (cachedResponse != null) {
                    log.info("Idempotent trade creation: returning cached response");
                    return objectMapper.readValue(cachedResponse, CreateTradeData.class);
                }
                throw new DomainConflictException("IDEMPOTENT_CONFLICT",
                        "Trade creation already in progress with this idempotency key");
            }

            // 2.0 服务端权威定价（A3）：以商品目录单价为准，拒绝客户端自定价格（demo 级缺陷修复）。
            if (priceAuthoritativeEnabled && skuPriceResolver != null
                    && command.getOrderLines() != null && !command.getOrderLines().isEmpty()) {
                skuPriceResolver.validateAndAttach(command.getOrderLines());
            }
            // 2. 调用 promotion quote（优惠报价）
            PromotionQuoteRequest quoteRequest = buildPromotionQuoteRequest(command);
            try {
                quoteResponse = promotionClient.quote(idempotencyKey, quoteRequest);
                log.info("Promotion quote succeeded: status={}, quoteId={}",
                        quoteResponse.getStatus(), quoteResponse.getQuoteId());
            } catch (Exception e) {
                log.error("Promotion quote failed", e);
                throw new DomainConflictException("PROMOTION_QUOTE_FAILED",
                        "Failed to get promotion quote: " + e.getMessage());
            }

            // 2.1 校验 promotion quote 响应关键字段
            if (quoteResponse == null || quoteResponse.getSnapshot() == null) {
                throw new DomainConflictException("PROMOTION_QUOTE_INVALID",
                        "Promotion quote response is null or missing snapshot for tradeId: "
                                + command.getTradeId());
            }
            if (quoteResponse.getQuoteId() == null || quoteResponse.getQuoteId().isEmpty()) {
                throw new DomainConflictException("PROMOTION_QUOTE_INVALID",
                        "Promotion quote response missing quoteId for tradeId: "
                                + command.getTradeId());
            }
            PromotionQuoteResponse.PricingSnapshot snapshot = quoteResponse.getSnapshot();
            if (snapshot.getVersion() == null || snapshot.getVersion().getInputHash() == null) {
                throw new DomainConflictException("PROMOTION_QUOTE_INVALID",
                        "Promotion quote response missing snapshot.version.inputHash for tradeId: "
                                + command.getTradeId());
            }

            // 2.2 处理 quote status
            String inputHash = snapshot.getVersion().getInputHash();
            if (quoteResponse.getStatus() == PromotionQuoteResponse.CheckoutResultStatus.REQUOTE_REQUIRED) {
                log.warn("Promotion quote requires re-quote: tradeId={}, changeReasons={}",
                        command.getTradeId(), quoteResponse.getChangeReasons());
                throw new DomainConflictException("PROMOTION_REQUOTE_REQUIRED",
                        "Promotion quote requires re-quote due to changes: "
                                + quoteResponse.getChangeReasons());
            }
            if (quoteResponse.getStatus() == PromotionQuoteResponse.CheckoutResultStatus.OK_WITH_CHANGE) {
                log.warn("Promotion quote changed: tradeId={}, changeReasons={}",
                        command.getTradeId(), quoteResponse.getChangeReasons());
                // 允许继续，但记录 changeReasons 供后续审计
            }

            // 2.3 从 snapshot 提取金额
            long totalAmountCents = snapshot.getItemsTotalCents() != null ? snapshot.getItemsTotalCents()
                    : 0L;
            long promotionDiscountCents = snapshot.getPromotionDiscountTotalCents() != null
                    ? snapshot.getPromotionDiscountTotalCents()
                    : 0L;
            long couponDiscountCents = snapshot.getCouponDiscountTotalCents() != null
                    ? snapshot.getCouponDiscountTotalCents()
                    : 0L;
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
            for (Map.Entry<String, List<CreateTradeCommand.OrderLineCommand>> entry : groupedByShop
                    .entrySet()) {
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
                        .inventoryStatus(InventoryStatus.PRE_DEDUCTED.getCode())
                        .promotionStatus(PromotionStatus.RESERVED.getCode())
                        .orderLines(orderLines)
                        .createdAt(LocalDateTime.now())
                        .build();
                shopOrders.add(shopOrder);
                log.info("ShopOrder created: orderId={}, shopId={}, sellerId={}", orderId, shopId,
                        sellerId);
            }

            // 4. 调用 inventory deduct（V2：按 shop 分组调用，Redis 原子扣减）
            compensationRequired = true;
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
                        .tradeId(tradeId)
                        .items(deductItems)
                        .build();

                // 为每个 shop 派生独立幂等键
                String shopIdempotencyKey = idempotencyKey + ":inv:deduct:" + shopId;
                InventoryDeductResponse deductResponse;
                try {
                    deductResponse = inventoryClient.preDeductRedisOnly(shopIdempotencyKey,
                            deductRequest);
                    if (deductResponse == null
                            || !Boolean.TRUE.equals(deductResponse.getSuccess())) {
                        String msg = deductResponse != null ? deductResponse.getMessage()
                                : "null response";
                        throw new DomainConflictException("INVENTORY_DEDUCT_FAILED",
                                "Inventory deduct failed for shop: " + shopId
                                        + ", msg: " + msg);
                    }
                    log.info("Inventory reserve succeeded for shop {}: occupyPairs={}",
                            shopId, deductResponse.getOccupyPairs());
                } catch (DomainConflictException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Inventory reserve failed for shop: {}", shopId, e);
                    throw new DomainConflictException("INVENTORY_DEDUCT_FAILED",
                            "Inventory deduct failed for shop: " + shopId + ", error: "
                                    + e.getMessage());
                }

                List<InventoryDeductResponse.OccupyPairDto> occupyPairs = deductResponse
                        .getOccupyPairs() != null
                                ? deductResponse.getOccupyPairs()
                                : List.of();
                if (occupyPairs.isEmpty()) {
                    throw new DomainConflictException("INVENTORY_DEDUCT_FAILED",
                            "Inventory reserve returned empty reservation refs for shop: "
                                    + shopId);
                }

                List<InventoryReservationRef> reservationRefs = occupyPairs.stream()
                        .map(dto -> new InventoryReservationRef(dto.getShopId(),
                                dto.getSkuId(), dto.getOccupyId()))
                        .collect(Collectors.toList());
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
                        .inventoryReservationRefs(reservationRefs)
                        .createdAt(shopOrder.getCreatedAt())
                        .build();
                log.info("ShopOrder updated with reservationRefs (canonical): orderId={}, refs={}",
                        shopOrder.getOrderId(), reservationRefs);

                // Write Outbox INVENTORY_RESERVE_DB events (async DB saveReservation via Kafka)
                for (int i = 0; i < reservationRefs.size(); i++) {
                    InventoryReservationRef ref = reservationRefs.get(i);
                    CreateTradeCommand.OrderLineCommand reserveItem = lines.get(i);
                    OrderDomainEvent reserveDbEvent = OrderDomainEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(OrderEventType.INVENTORY_RESERVE_DB)
                            .aggregateType("INVENTORY")
                            .aggregateId(ref.getReservationId())
                            .occurredAt(LocalDateTime.now())
                            .traceId(command.getTraceId())
                            .payloadJson(objectMapper.writeValueAsString(Map.of(
                                    "reservationId", ref.getReservationId(),
                                    "shopId", ref.getShopId(),
                                    "skuId", ref.getSkuId(),
                                    "quantity", reserveItem.getQuantity(),
                                    "tradeId", tradeId,
                                    "orderId", shopOrder.getOrderId(),
                                    "expireAt", OffsetDateTime.now().plusMinutes(reservationTtlMinutes).toString())))
                            .build();
                    outboxEventService.saveEvent(reserveDbEvent);
                }

                updatedOrders.add(updatedShopOrder);
            }

            // 5. promotion commit：异步时写 Outbox（promotion consumer 预占并回执），同步时走 Feign
            PromotionCommitRequest promotionCommitRequest = PromotionCommitRequest.builder()
                    .quoteId(quoteResponse.getQuoteId())
                    .tradeId(tradeId)
                    .inputHash(inputHash)
                    .build();
            if (promotionCommitAsyncEnabled) {
                writePromotionCommitOutbox(tradeId, quoteResponse.getQuoteId(), inputHash,
                        command.getTraceId());
            } else {
                try {
                    promotionClient.commit(idempotencyKey, promotionCommitRequest);
                    log.info("Promotion commit succeeded");
                } catch (Exception e) {
                    log.error("Promotion commit failed", e);
                    throw new DomainConflictException("PROMOTION_COMMIT_FAILED",
                            "Promotion commit failed for tradeId: " + tradeId + ", error: "
                                    + e.getMessage());
                }
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
                throw new DomainConflictException("ORDER_SAVE_FAILED",
                        "Failed to save shop orders for tradeId: " + tradeId);
            }
            log.info("Trade created with promotionQuoteId: {}, inputHash: {}", quoteResponse.getQuoteId(),
                    inputHash);

            // 6. 生成 PaymentIntent
            String paymentId = TradeIdGenerator.generatePaymentIntentId();

            // 计算支付超时时间（默认15分钟）
            LocalDateTime expireAt = LocalDateTime.now().plusSeconds(paymentTimeoutSeconds);

            PaymentIntentEntity paymentIntent = PaymentIntentEntity.builder()
                    .paymentId(paymentId)
                    .tradeId(trade.getTradeId())
                    .amountCents(trade.getPayableAmountCents())
                    .buyerId(trade.getBuyerId()) // 新增：买家ID
                    .payChannel("DEFAULT") // 新增：默认支付渠道（可由前端传入）
                    .expireAt(expireAt) // 新增：支付超时时间
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
                            "payableAmountCents", trade.getPayableAmountCents())))
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
                                "sellerId", shopOrder.getSellerId())))
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
                            "paymentId", paymentId, // 保留旧字段名兼容
                            "paymentIntentId", paymentId, // 新增：明确语义
                            "tradeId", trade.getTradeId(),
                            "amountCents", trade.getPayableAmountCents(),
                            "buyerId", trade.getBuyerId(), // 新增：买家ID
                            "payChannel", paymentIntent.getPayChannel(), // 新增：支付渠道
                            "expireAt", expireAt.toString() // 新增：支付超时时间
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
            if (compensationRequired) {
                try {
                    compensateCreateTradeFailure(idempotencyKey, tradeId, quoteResponse,
                            updatedOrders,
                            resolveCreateTradeFailureReason(e));
                } catch (DomainConflictException compensationException) {
                    log.error("Trade creation compensation failed: tradeId={}", tradeId,
                            compensationException);
                    throw compensationException;
                }
            }
            log.error("Trade creation failed", e);
            throw e;
        }
    }

    /**
     * 写 PROMOTION_COMMIT Outbox 事件（异步 promotion commit 路径）。
     * 包级可见以支持单元测试（同包测试直接调用验证事件构造）。
     */
    void writePromotionCommitOutbox(String tradeId, String quoteId, String inputHash,
                                    String traceId) throws JsonProcessingException {
        OrderDomainEvent commitEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.PROMOTION_COMMIT)
                .aggregateType("PROMOTION")
                .aggregateId(quoteId)
                .occurredAt(LocalDateTime.now())
                .traceId(traceId)
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                        "quoteId", quoteId,
                        "tradeId", tradeId,
                        "inputHash", inputHash)))
                .build();
        outboxEventService.saveEvent(commitEvent);
        log.info("Promotion commit outbox written (async): tradeId={}, quoteId={}", tradeId, quoteId);
    }

    private void compensateCreateTradeFailure(String idempotencyKey,
            String tradeId,
            PromotionQuoteResponse quoteResponse,
            List<ShopOrder> reservedOrders,
            String failureReason) {
        List<String> compensationErrors = new ArrayList<>();

        for (ShopOrder reservedOrder : reservedOrders) {
            try {
                releaseReservedInventoryForCreateTradeCompensation(idempotencyKey, reservedOrder);
            } catch (Exception compensationError) {
                log.error("Failed to compensate inventory reserve: tradeId={}, orderId={}",
                        tradeId, reservedOrder.getOrderId(), compensationError);
                compensationErrors.add("inventory[orderId=" + reservedOrder.getOrderId()
                        + ", shopId=" + reservedOrder.getShopId()
                        + "]: " + compensationError.getMessage());
            }
        }

        if (quoteResponse != null && quoteResponse.getQuoteId() != null
                && !quoteResponse.getQuoteId().isEmpty()) {
            try {
                releasePromotionForCreateTradeCompensation(idempotencyKey, quoteResponse.getQuoteId(),
                        tradeId, failureReason);
            } catch (Exception compensationError) {
                log.error("Failed to compensate promotion quote: tradeId={}, quoteId={}",
                        tradeId, quoteResponse.getQuoteId(), compensationError);
                compensationErrors.add("promotion[quoteId=" + quoteResponse.getQuoteId()
                        + "]: " + compensationError.getMessage());
            }
        }

        if (!compensationErrors.isEmpty()) {
            throw new DomainConflictException("CREATE_TRADE_COMPENSATION_FAILED",
                    "Create trade compensation failed for tradeId: " + tradeId + ", details: "
                            + String.join("; ", compensationErrors));
        }
    }

    private void releaseReservedInventoryForCreateTradeCompensation(String idempotencyKey,
            ShopOrder shopOrder) {
        if (shopOrder.getInventoryReservationRefs() == null
                || shopOrder.getInventoryReservationRefs().isEmpty()) {
            throw new IllegalStateException("Missing inventory reservation refs for create compensation, orderId="
                    + shopOrder.getOrderId());
        }
        // Sync Redis rollback for createTrade compensation (error path, not hot path)
        for (InventoryReservationRef ref : shopOrder.getInventoryReservationRefs()) {
            try {
                inventoryClient.rollbackRedis(
                        idempotencyKey + ":inv:rollback:" + shopOrder.getShopId(),
                        Map.of("shopId", ref.getShopId(), "skuId", ref.getSkuId(),
                                "reservationId", ref.getReservationId()));
            } catch (Exception e) {
                log.error("Redis rollback failed during compensation: orderId={}, reservationId={}",
                        shopOrder.getOrderId(), ref.getReservationId(), e);
            }
        }
        log.info("Compensated inventory reserve (Redis rollback): orderId={}, shopId={}",
                shopOrder.getOrderId(), shopOrder.getShopId());
    }

    private void releasePromotionForCreateTradeCompensation(String idempotencyKey,
            String quoteId,
            String tradeId,
            String failureReason) {
        PromotionReleaseResponse releaseResponse = promotionClient.release(idempotencyKey,
                PromotionReleaseRequest.builder()
                        .quoteId(quoteId)
                        .tradeId(tradeId)
                        .reason(failureReason)
                        .build());
        if (releaseResponse == null || !Boolean.TRUE.equals(releaseResponse.getSuccess())) {
            String message = releaseResponse != null ? releaseResponse.getMessage() : "null response";
            throw new IllegalStateException("Promotion compensation release failed for quoteId="
                    + quoteId + ", message=" + message);
        }
        log.info("Compensated promotion quote: tradeId={}, quoteId={}", tradeId, quoteId);
    }

    private String resolveCreateTradeFailureReason(Exception e) {
        if (e instanceof DomainConflictException domainConflictException) {
            return domainConflictException.getErrorCode();
        }
        return "CREATE_TRADE_FAILED";
    }

    /**
     * 取消交易 Saga（仅未支付订单可取消）
     *
     * @param idempotencyKey 幂等键
     * @param command        取消交易命令
     */
    @Transactional(rollbackFor = Exception.class)
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

            cancelTradeInternal(idempotencyKey, command);
        } catch (Exception e) {
            log.error("Trade cancellation failed", e);
            throw e;
        }
    }

    /**
     * 取消交易核心流程（复用：公共 cancelTrade 与内部 autoCancelTrade 均走此路径）。
     * 释放库存（outbox INVENTORY_RELEASE）+ closeTrade + TRADE_CLOSED/ORDER_CLOSED outbox
     * + promotion release（同步 Feign，异步化不在本计划范围）。
     */
    private void cancelTradeInternal(String idempotencyKey, CancelTradeCommand command) throws Exception {
        // 获取 Trade 聚合根（公共 cancelTrade 已校验过；此处供 autoCancelTrade 直接复用，
        // 同一事务内二次查找命中同一持久化上下文）
        Trade trade = tradeRepository.findByTradeId(command.getTradeId())
                .orElseThrow(() -> new DomainConflictException("TRADE_NOT_FOUND",
                        "Trade not found: " + command.getTradeId()));

        // 获取所有子单
        List<ShopOrder> shopOrders = shopOrderRepository.findByTradeId(command.getTradeId());

        // 同步补偿：释放库存和优惠
        // 按 ShopOrder 遍历释放库存（VERSION_2 用 canonical release，VERSION_1 用 legacy
        // releaseV2）
        for (ShopOrder shopOrder : shopOrders) {
            releaseInventoryForCancelledShopOrder(idempotencyKey, command, trade.getTradeId(),
                    shopOrder);
        }

        // 所有 release 成功后，才更新本地关闭态与关闭事件
        trade.closeTrade();
        tradeRepository.save(trade);

        for (ShopOrder shopOrder : shopOrders) {
            // Mark inventory status as RELEASED before closing the order
            shopOrder.markInventoryReleased();
            shopOrder.close();
            shopOrderRepository.save(shopOrder);
        }

        OrderDomainEvent tradeClosedEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.TRADE_CLOSED)
                .aggregateType("TRADE")
                .aggregateId(trade.getTradeId())
                .occurredAt(LocalDateTime.now())
                .traceId(command.getTraceId())
                .payloadJson(objectMapper.writeValueAsString(Map.of(
                        "tradeId", trade.getTradeId(),
                        "reason", command.getReason())))
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
                            "tradeId", shopOrder.getTradeId())))
                    .build();
            outboxEventService.saveEvent(orderClosedEvent);
        }

        // 释放促销优惠
        try {
            // 校验 promotionQuoteId 已绑定
            if (trade.getPromotionQuoteId() == null || trade.getPromotionQuoteId().isEmpty()) {
                log.warn("Missing promotionQuoteId for trade: {}, skipping promotion release",
                        trade.getTradeId());
            } else {
                PromotionReleaseRequest promotionReleaseRequest = PromotionReleaseRequest
                        .builder()
                        .quoteId(trade.getPromotionQuoteId())
                        .tradeId(trade.getTradeId())
                        .reason(command.getReason())
                        .build();
                promotionClient.release(idempotencyKey, promotionReleaseRequest);
                log.info("Promotion released for cancelled trade: tradeId={}",
                        trade.getTradeId());
            }
        } catch (Exception e) {
            log.error("Failed to release promotion for cancelled trade: tradeId={}",
                    trade.getTradeId(), e);
        }

        log.info("Trade cancelled: tradeId={}", trade.getTradeId());
    }

    private void releaseInventoryForCancelledShopOrder(String idempotencyKey,
            CancelTradeCommand command,
            String tradeId,
            ShopOrder shopOrder) {
        try {
            if (shopOrder.getInventoryReservationRefs() == null
                    || shopOrder.getInventoryReservationRefs().isEmpty()) {
                throw new DomainConflictException(
                        "INVENTORY_RELEASE_CONFLICT",
                        "Missing inventory reservation refs during cancel for trade: " + tradeId
                                + ", orderId=" + shopOrder.getOrderId());
            }
            // Async release: write Outbox INVENTORY_RELEASE event (replaces sync Feign releaseCanonical)
            OrderDomainEvent releaseEvent = OrderDomainEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(OrderEventType.INVENTORY_RELEASE)
                    .aggregateType("INVENTORY")
                    .aggregateId(shopOrder.getOrderId())
                    .occurredAt(LocalDateTime.now())
                    .traceId(command.getTraceId())
                    .payloadJson(objectMapper.writeValueAsString(Map.of(
                            "tradeId", tradeId,
                            "orderId", shopOrder.getOrderId(),
                            "reason", command.getReason(),
                            "occupyPairs", shopOrder.getInventoryReservationRefs().stream()
                                    .map(r -> Map.of("shopId", r.getShopId(), "skuId", r.getSkuId(),
                                            "occupyId", r.getReservationId()))
                                    .collect(Collectors.toList()))))
                    .build();
            outboxEventService.saveEvent(releaseEvent);
            log.info("Inventory release event written (async): orderId={}, shopId={}",
                    shopOrder.getOrderId(), shopOrder.getShopId());
        } catch (Exception e) {
            log.error("Failed to release inventory for cancelled trade: tradeId={}, orderId={}",
                    tradeId, shopOrder.getOrderId(), e);
            throw new DomainConflictException(
                    "INVENTORY_RELEASE_CONFLICT",
                    "Inventory release conflict during cancel for trade: " + tradeId);
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
            PaymentIntentEntity paymentIntent = paymentIntentJpaRepository
                    .findByPaymentId(command.getPaymentId())
                    .orElseThrow(() -> new DomainConflictException("PAYMENT_INTENT_NOT_FOUND",
                            "Payment intent not found: " + command.getPaymentId()));

            if ("PAID".equals(paymentIntent.getStatus())) {
                log.info("Payment already processed (idempotent): paymentId={}",
                        command.getPaymentId());
                return;
            }

            // 支付门控（仅异步 promotion commit 启用时生效）：仅 COMMITTED 的 trade 可支付。
            // 幂等早退之后执行，避免重复回调在 trade 已关闭时误报错误。
            if (promotionCommitAsyncEnabled) {
                ensureTradePayable(command.getTradeId());
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

            List<ShopOrder> shopOrders = shopOrderRepository.findByTradeId(trade.getTradeId());
            // 同步确认：调用 inventory confirm（canonical 路径）
            long paidAtEpochMs = System.currentTimeMillis();
            for (ShopOrder shopOrder : shopOrders) {
                if (shopOrder.getInventoryReservationRefs() == null
                        || shopOrder.getInventoryReservationRefs().isEmpty()) {
                    throw new IllegalStateException(
                            "Missing inventory reservation refs for shopOrder: "
                                    + shopOrder.getOrderId());
                }
                List<InventoryConfirmRequest.OccupyPairDto> confirmPairs = shopOrder
                        .getInventoryReservationRefs().stream()
                        .map(r -> InventoryConfirmRequest.OccupyPairDto.builder()
                                .shopId(r.getShopId()).skuId(r.getSkuId())
                                .occupyId(r.getReservationId()).build())
                        .collect(Collectors.toList());

                // Async confirm: write Outbox INVENTORY_CONFIRM event (replaces sync Feign confirmReservation)
                OrderDomainEvent confirmEvent = OrderDomainEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(OrderEventType.INVENTORY_CONFIRM)
                        .aggregateType("INVENTORY")
                        .aggregateId(shopOrder.getOrderId())
                        .occurredAt(LocalDateTime.now())
                        .traceId(command.getTraceId())
                        .payloadJson(objectMapper.writeValueAsString(Map.of(
                                "paymentId", command.getPaymentId(),
                                "tradeId", command.getTradeId(),
                                "orderId", shopOrder.getOrderId(),
                                "occupyPairs", shopOrder.getInventoryReservationRefs().stream()
                                        .map(r -> Map.of("shopId", r.getShopId(), "skuId", r.getSkuId(),
                                                "occupyId", r.getReservationId()))
                                        .collect(Collectors.toList()))))
                        .build();
                outboxEventService.saveEvent(confirmEvent);
                log.info("Inventory confirm event written (async): orderId={}, shopId={}",
                        shopOrder.getOrderId(), shopOrder.getShopId());
            }

            // 所有 confirm 成功后，才允许推进 paid 投影与事件
            trade.markAsPaid();
            tradeRepository.save(trade);

            for (ShopOrder shopOrder : shopOrders) {
                ShopOrder updated = ShopOrder.builder()
                        .id(shopOrder.getId())
                        .orderId(shopOrder.getOrderId())
                        .tradeId(shopOrder.getTradeId())
                        .shopId(shopOrder.getShopId())
                        .sellerId(shopOrder.getSellerId())
                        .orderStatus(OrderStatus.PENDING_SHIP)
                        .inventoryStatus(InventoryStatus.CONFIRMED.getCode())
                        .promotionStatus(shopOrder.getPromotionStatus())
                        .inventoryReservationRefs(shopOrder.getInventoryReservationRefs())
                        .totalAmountCents(shopOrder.getTotalAmountCents())
                        .orderLines(shopOrder.getOrderLines())
                        .createdAt(shopOrder.getCreatedAt())
                        .updatedAt(shopOrder.getUpdatedAt())
                        .acceptedAt(shopOrder.getAcceptedAt())
                        .build();
                shopOrderRepository.save(updated);
            }

            paymentIntent.setStatus("PAID");
            paymentIntent.setPaidAt(LocalDateTime.now());
            paymentIntentJpaRepository.save(paymentIntent);

            // 同步确认：promotion commit
            try {
                // 校验 promotionQuoteId/inputHash 已绑定
                if (trade.getPromotionQuoteId() == null || trade.getPromotionQuoteId().isEmpty()) {
                    log.warn("Missing promotionQuoteId for trade: {}, skipping promotion commit",
                            trade.getTradeId());
                } else if (trade.getPromotionInputHash() == null
                        || trade.getPromotionInputHash().isEmpty()) {
                    log.warn("Missing promotionInputHash for trade: {}, skipping promotion commit",
                            trade.getTradeId());
                } else {
                    PromotionCommitRequest promotionCommitRequest = PromotionCommitRequest.builder()
                            .quoteId(trade.getPromotionQuoteId())
                            .tradeId(trade.getTradeId())
                            .inputHash(trade.getPromotionInputHash())
                            .payNo(command.getPaymentId())
                            .paidAt(paidAtEpochMs)
                            .build();
                    PromotionCommitResponse promotionCommitResponse = promotionClient
                            .commit(idempotencyKey, promotionCommitRequest);

                    // 处理 promotion commit 响应状态
                    if (promotionCommitResponse != null && promotionCommitResponse
                            .getStatus() == PromotionQuoteResponse.CheckoutResultStatus.REQUOTE_REQUIRED) {
                        log.error("Promotion commit requires re-quote after payment: tradeId={}, changeReasons={}",
                                trade.getTradeId(),
                                promotionCommitResponse.getChangeReasons());
                        // 严重错误：支付后无法重新报价，记录错误但不阻断流程
                        // 实际生产中应触发人工介入或补偿流程
                    } else {
                        log.info("Promotion committed for paid trade: tradeId={}, status={}",
                                trade.getTradeId(),
                                promotionCommitResponse != null
                                        ? promotionCommitResponse.getStatus()
                                        : "null");
                    }
                }
            } catch (Exception e) {
                log.error("Failed to commit promotion for paid trade: tradeId={}", trade.getTradeId(),
                        e);
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
                            "paidAmountCents", command.getPaidAmountCents())))
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
                                "tradeId", shopOrder.getTradeId())))
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

    private void publishInventoryConfirmConflictEvent(PaymentSucceededCommand command,
            ShopOrder shopOrder,
            List<InventoryConfirmRequest.OccupyPairDto> confirmPairs,
            InventoryConfirmResponse confirmResp) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", command.getTradeId());
        payload.put("orderId", shopOrder.getOrderId());
        payload.put("occupyPairs", confirmPairs);
        payload.put("conflictReservationIds",
                confirmResp.getConflictReservationIds() != null
                        ? confirmResp.getConflictReservationIds()
                        : List.of());
        payload.put("conflictReason", confirmResp.getMessage() != null ? confirmResp.getMessage()
                : "inventory confirm conflict");
        payload.put("occurredAt", LocalDateTime.now().toString());

        OrderDomainEvent conflictEvent = OrderDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(OrderEventType.INVENTORY_CONFIRM_CONFLICT)
                .aggregateType("ORDER")
                .aggregateId(shopOrder.getOrderId())
                .occurredAt(LocalDateTime.now())
                .traceId(command.getTraceId())
                .payloadJson(objectMapper.writeValueAsString(payload))
                .build();
        boolean saved = outboxEventService.saveEventInNewTransaction(conflictEvent);
        if (!saved) {
            log.error("Failed to persist INVENTORY_CONFIRM_CONFLICT event: tradeId={}, orderId={}",
                    command.getTradeId(), shopOrder.getOrderId());
            throw new IllegalStateException("Failed to persist INVENTORY_CONFIRM_CONFLICT event");
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
                    .orElseThrow(() -> new DomainConflictException("TRADE_NOT_FOUND",
                            "Trade not found: " + tradeId));

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
                                    "tradeId", tradeId)))
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

    /**
     * 支付门控：仅 COMMITTED 状态的 trade 可支付。
     * 仅异步 promotion commit 启用（promotionCommitAsyncEnabled=true）时由
     * onPaymentSucceeded 调用；flag 关闭时保持现状支付行为（不校验）。
     *
     * @param tradeId 交易ID
     * @throws DomainConflictException TRADE_NOT_FOUND（trade 不存在）或
     *                                 TRADE_NOT_READY_FOR_PAYMENT（非 COMMITTED）
     */
    public void ensureTradePayable(String tradeId) {
        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new DomainConflictException("TRADE_NOT_FOUND",
                        "Trade not found: " + tradeId));
        if (trade.isClosed() || "FAILED".equals(trade.getPromotionCommitStatus())) {
            // 终态：payment 回调应停止重试（TRADE_TERMINAL）
            throw new DomainConflictException("TRADE_TERMINAL",
                    "Trade promotion commit status is " + trade.getPromotionCommitStatus()
                            + ", trade is terminal (cancelled/failed)");
        }
        if (!"COMMITTED".equals(trade.getPromotionCommitStatus())) {
            throw new DomainConflictException("TRADE_NOT_READY_FOR_PAYMENT",
                    "Trade promotion commit status is " + trade.getPromotionCommitStatus()
                            + ", only COMMITTED trades are payable");
        }
    }

    /**
     * 应用 promotion commit 回执结果（由回执 consumer 调用）：
     * 成功 → COMMITTED；失败 → FAILED + 自动取消（未支付订单必为未支付，可直接关闭）。
     * 幂等：对已关闭/已 FAILED 的 trade 忽略重复回执（不重复触发自动取消）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyPromotionCommitResult(String tradeId, boolean committed, String reason) {
        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new DomainConflictException("TRADE_NOT_FOUND",
                        "Trade not found: " + tradeId));
        if (committed) {
            if (trade.isClosed()) {
                log.warn("Promotion commit ack for already closed trade, compensating release: tradeId={}", tradeId);
                compensatePromotionRelease(trade);
                return;
            }
            trade.markPromotionCommitted();
            tradeRepository.save(trade);
            log.info("Trade promotion commit confirmed: tradeId={}", tradeId);
            return;
        }
        // FAILED 回执：预占失败 → 券未锁定，无泄漏；按状态机幂等忽略重复回执（避免重复自动取消）
        if (trade.isClosed() || "FAILED".equals(trade.getPromotionCommitStatus())) {
            log.warn("Promotion commit failure ack ignored, trade already failed/closed: tradeId={}",
                    tradeId);
            return;
        }
        trade.markPromotionCommitFailed();
        tradeRepository.save(trade);
        log.info("Trade promotion commit failed, auto-cancelling: tradeId={}, reason={}", tradeId, reason);
        autoCancelTrade(tradeId, "PROMOTION_COMMIT_FAILED:" + reason, "auto-cancel-" + tradeId);
    }

    /**
     * 补偿释放：已关闭订单收到回执时，释放 promotion 已预占的券（防券泄漏）。
     * 幂等——promotion release 对未锁定/已释放 quote 无副作用；失败仅告警不阻断。
     */
    private void compensatePromotionRelease(Trade trade) {
        if (trade.getPromotionQuoteId() == null || trade.getPromotionQuoteId().isEmpty()) {
            return;
        }
        try {
            PromotionReleaseRequest releaseRequest = PromotionReleaseRequest.builder()
                    .quoteId(trade.getPromotionQuoteId())
                    .tradeId(trade.getTradeId())
                    .reason("TRADE_CLOSED_BEFORE_ACK")
                    .build();
            promotionClient.release("ack-compensate-" + trade.getTradeId(), releaseRequest);
        } catch (Exception e) {
            log.error("Failed to compensate promotion release for closed trade: tradeId={}",
                    trade.getTradeId(), e);
        }
    }

    /**
     * 自动取消（供 commit 失败与 PENDING 超时兜底使用）：复用 cancelTrade 核心流程，
     * 生成内部 idempotencyKey 与 traceId。
     * 异常以 unchecked 重抛（checked 异常会破坏回执 consumer 的精确重抛分析），
     * 事务回滚 + consumer 重试/DLT 语义保持不变。
     */
    @Transactional(rollbackFor = Exception.class)
    public void autoCancelTrade(String tradeId, String reason, String traceId) {
        String internalKey = "auto-cancel-" + tradeId + "-" + System.nanoTime();
        CancelTradeCommand command = CancelTradeCommand.builder()
                .tradeId(tradeId)
                .reason(reason)
                .traceId(traceId)
                .build();
        try {
            cancelTradeInternal(internalKey, command);
        } catch (Exception e) {
            log.error("Auto-cancel failed for tradeId={}, will retry via scheduler", tradeId, e);
            throw new IllegalStateException("Auto-cancel failed for tradeId=" + tradeId, e);
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
                    .platformCouponIds(
                            platformCodes != null ? platformCodes : Collections.emptyList())
                    .shopCouponIdsByShop(
                            shopCodesMap != null ? shopCodesMap : Collections.emptyMap())
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
