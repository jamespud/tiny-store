package com.github.spud.tinystore.order.application.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.github.spud.tinystore.order.domain.enums.BuyerType;
import com.github.spud.tinystore.order.domain.model.Buyer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.application.command.*;
import com.github.spud.tinystore.order.application.result.CreateOrderResult;
import com.github.spud.tinystore.order.application.result.PreviewOrderResult;
import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OutboxEventEnvelope;
import com.github.spud.tinystore.order.domain.model.Coupon;
import com.github.spud.tinystore.order.domain.model.Order;
import com.github.spud.tinystore.order.domain.model.Product;
import com.github.spud.tinystore.order.domain.repository.IdempotencyRepository;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import com.github.spud.tinystore.order.domain.service.*;
import com.github.spud.tinystore.order.domain.status.CoreFlowStatus;
import com.github.spud.tinystore.order.domain.status.OrderStateTransitionService;

import lombok.extern.slf4j.Slf4j;

/**
 * Order Application Service - Enhanced with Rich Aggregate Pattern
 * 
 * @author Spud
 * @date 2025/9/3
 */
@Slf4j
@Service
public class OrderApplicationService {

    // Legacy dependencies (keep existing functionality)
    private final OrderDomainService orderDomainService;
    private final CancelDecisionService cancelDecisionService;
    private final OrderStatusTranslator orderStatusTranslator;
    private final OrderStateTransitionService orderStateTransitionService;
    
    // New rich aggregate dependencies
    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${order.preview.ttl:60}")
    private long previewTTL = 60;

    private final CouponService couponService;
    private final ProductService productService;
    private final OrderPriceCalculationService orderPriceCalculationService;
    private final ProductValidatorService productValidatorService;
    private final UserValidatorService userValidatorService;
    private final OrderFactory orderFactory;
    private final OrderOutboxService orderOutboxService;

    public OrderApplicationService(CouponService couponService,
                                   OrderPriceCalculationService priceCalculationService,
                                   ProductValidatorService productValidatorService, 
                                   UserValidatorService userValidatorService,
                                   ProductService productService, 
                                   OrderFactory orderFactory,
                                   OrderOutboxService orderOutboxService, 
                                   OrderDomainService orderDomainService,
                                   CancelDecisionService cancelDecisionService, 
                                   OrderStatusTranslator orderStatusTranslator,
                                   OrderStateTransitionService orderStateTransitionService,
                                   OrderRepository orderRepository,
                                   IdempotencyRepository idempotencyRepository,
                                   ObjectMapper objectMapper) {
        this.couponService = couponService;
        this.productService = productService;
        this.orderPriceCalculationService = priceCalculationService;
        this.productValidatorService = productValidatorService;
        this.userValidatorService = userValidatorService;
        this.orderFactory = orderFactory;
        this.orderOutboxService = orderOutboxService;
        this.orderDomainService = orderDomainService;
        this.cancelDecisionService = cancelDecisionService;
        this.orderStatusTranslator = orderStatusTranslator;
        this.orderStateTransitionService = orderStateTransitionService;
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    // ========== NEW RICH AGGREGATE METHODS ==========

    /**
     * Create order using rich aggregate
     */
    @Transactional
    public String createOrder(CreateOrderCommand cmd) {
        log.info("Creating order for buyer: {}", cmd.getUserId());
        
        // External validations
        validateOrderCreation(cmd);
        
        // Create order aggregate
        Order order = Order.create(Order.CreateOrderArgs.builder()
                .buyer(new Buyer(cmd.getUserId(), BuyerType.NORMAL, 1))
                .subOrders(cmd.getSubOrders())
                .products(cmd.getProducts())
                .coupons(cmd.getCoupons())
                .discounts(cmd.getDiscounts())
                .couponAllocations(cmd.getCouponAllocations())
                .discountAllocations(cmd.getDiscountAllocations())
                .pricingSummary(cmd.getPricingSummary())
                .reservations(cmd.getReservations())
                .address(cmd.getAddress())
                .deviceId(cmd.getDeviceId())
                .build());
        
        // Save with events
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("Order created: {}", order.getOrderId());
        return order.getOrderId();
    }

    /**
     * Handle payment success callback (with idempotency)
     */
    @Transactional
    public void handlePaymentSucceeded(PaymentSucceededCommand cmd) {
        String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
                .forPaymentCallback(cmd.getPaymentId(), cmd.getOrderId(), cmd.getAmount().toString());
        
        if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
            log.info("Payment callback already processed: {}", idempotencyKey);
            return; // Already processed
        }
        
        try {
            log.info("Processing payment success for order: {}", cmd.getOrderId());
            
            Order order = orderRepository.findById(cmd.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
            
            order.onPaymentSuccess(Order.PaymentSuccessArgs.builder()
                    .paymentId(cmd.getPaymentId())
                    .amount(cmd.getAmount())
                    .isDeposit(cmd.isDeposit())
                    .isFinalPayment(cmd.isFinalPayment())
                    .build());
            
            List<OrderDomainEvent> events = order.pullDomainEvents();
            List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
            orderRepository.saveWithOutbox(order, envelopes);
            
            log.info("Payment success processed for order: {}", cmd.getOrderId());
        } catch (Exception e) {
            idempotencyRepository.release(idempotencyKey, "order-service");
            throw e;
        }
    }

    /**
     * Merchant receives order
     */
    @Transactional
    public void merchantReceive(MerchantReceiveCommand cmd) {
        log.info("Merchant receiving order: {}", cmd.getOrderId());
        
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        
        order.onMerchantReceive();
        
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("Merchant receive processed for order: {}", cmd.getOrderId());
    }

    /**
     * Ship order
     */
    @Transactional
    public void ship(ShipCommand cmd) {
        log.info("Shipping order: {}", cmd.getOrderId());
        
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        
        order.onShip(Order.ShipArgs.builder()
                .subOrderId(cmd.getSubOrderId())
                .shipmentInfo(cmd.getShipmentInfo())
                .build());
        
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("Ship processed for order: {}", cmd.getOrderId());
    }

    /**
     * Handle delivery callback (with idempotency)
     */
    @Transactional
    public void handleDelivered(DeliveredCommand cmd) {
        String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
                .forLogisticsCallback(cmd.getOrderId(), "DELIVERED", cmd.getIdempotencyKey());
        
        if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
            log.info("Delivery callback already processed: {}", idempotencyKey);
            return;
        }
        
        try {
            log.info("Processing delivery for order: {}", cmd.getOrderId());
            
            Order order = orderRepository.findById(cmd.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
            
            order.onDelivered(Order.DeliveredArgs.builder()
                    .shipmentInfo(cmd.getShipmentInfo())
                    .afterSaleWindowOpen(cmd.isAfterSaleWindowOpen())
                    .build());
            
            List<OrderDomainEvent> events = order.pullDomainEvents();
            List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
            orderRepository.saveWithOutbox(order, envelopes);
            
            log.info("Delivery processed for order: {}", cmd.getOrderId());
        } catch (Exception e) {
            idempotencyRepository.release(idempotencyKey, "order-service");
            throw e;
        }
    }

    /**
     * Auto complete order
     */
    @Transactional
    public void autoComplete(AutoCompleteCommand cmd) {
        log.info("Auto completing order: {}", cmd.getOrderId());
        
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        
        order.onAutoComplete();
        
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("Auto complete processed for order: {}", cmd.getOrderId());
    }

    /**
     * Apply for after sale
     */
    @Transactional
    public void applyAfterSale(ApplyAfterSaleCommand cmd) {
        log.info("Applying after sale for order: {}", cmd.getOrderId());
        
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        
        order.requestAfterSale();
        
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("After sale application processed for order: {}", cmd.getOrderId());
    }

    /**
     * Handle refund success callback (with idempotency)
     */
    @Transactional
    public void handleRefundSucceeded(RefundSucceededCommand cmd) {
        String idempotencyKey = IdempotencyRepository.IdempotencyKeyGenerator
                .forRefundCallback(cmd.getRefundId(), cmd.getOrderId());
        
        if (!idempotencyRepository.tryAcquire(idempotencyKey, "order-service", Duration.ofHours(1))) {
            log.info("Refund callback already processed: {}", idempotencyKey);
            return;
        }
        
        try {
            log.info("Processing refund success for order: {}", cmd.getOrderId());
            
            Order order = orderRepository.findById(cmd.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
            
            order.onRefundSuccess();
            
            List<OrderDomainEvent> events = order.pullDomainEvents();
            List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
            orderRepository.saveWithOutbox(order, envelopes);
            
            log.info("Refund success processed for order: {}", cmd.getOrderId());
        } catch (Exception e) {
            idempotencyRepository.release(idempotencyKey, "order-service");
            throw e;
        }
    }

    /**
     * Apply for cancellation
     */
    @Transactional
    public void applyCancel(ApplyCancelCommand cmd) {
        log.info("Applying cancel for order: {}", cmd.getOrderId());
        
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        
        order.requestCancel();
        
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("Cancel application processed for order: {}", cmd.getOrderId());
    }

    /**
     * Approve cancellation
     */
    @Transactional
    public void approveCancel(ApproveCancelCommand cmd) {
        log.info("Approving cancel for order: {}", cmd.getOrderId());
        
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        
        order.approveCancel();
        
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("Cancel approval processed for order: {}", cmd.getOrderId());
    }

    /**
     * Reject cancellation
     */
    @Transactional
    public void rejectCancel(RejectCancelCommand cmd) {
        log.info("Rejecting cancel for order: {}", cmd.getOrderId());
        
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        
        order.rejectCancel();
        
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("Cancel rejection processed for order: {}", cmd.getOrderId());
    }

    /**
     * Handle exchange completion
     */
    @Transactional
    public void exchangeCompleted(ExchangeCompletedCommand cmd) {
        log.info("Processing exchange completion for order: {}", cmd.getOrderId());
        
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        
        order.onExchangeCompleted();
        
        List<OrderDomainEvent> events = order.pullDomainEvents();
        List<OutboxEventEnvelope> envelopes = mapToOutboxEnvelopes(events);
        orderRepository.saveWithOutbox(order, envelopes);
        
        log.info("Exchange completion processed for order: {}", cmd.getOrderId());
    }

    // Helper methods
    private void validateOrderCreation(CreateOrderCommand cmd) {
        // Use existing validation methods
        validateOrderParam(cmd.getBuyer().userId(), 
                           cmd.getProducts().keySet().stream().map(Product::getProductId).toList(), 
                           cmd.getCoupons().stream().map(Coupon::getCouponId).collect(java.util.stream.Collectors.toSet()), 
                           cmd.getAddress().getAddressId());
    }

    private List<OutboxEventEnvelope> mapToOutboxEnvelopes(List<OrderDomainEvent> events) {
        return events.stream()
                .map(event -> {
                    String topic = determineTopicForEvent(event);
                    String payload = serializeEvent(event);
                    return OutboxEventEnvelope.fromDomainEvent(event, topic, payload);
                })
                .toList();
    }

    private String determineTopicForEvent(OrderDomainEvent event) {
        return switch (event.getEventType()) {
            case ORDER_CREATED -> "order.created";
            case ORDER_PAID -> "order.payment.succeeded";
            case ORDER_SHIPPED -> "order.shipped";
            case ORDER_COMPLETED -> "order.completed";
            case ORDER_CANCELLED -> "order.cancelled";
            case AFTERSALE_REQUESTED -> "order.refund.requested";
            case AFTERSALE_COMPLETED -> "order.refund.completed";
            default -> "order.general";
        };
    }

    private String serializeEvent(OrderDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    // ========== LEGACY METHODS (PRESERVED) ==========

    /**
     * 订单预览 验证商品是否下架，计算价格，缓存预览结果
     *
     * @param cmd
     * @return
     */
    public PreviewOrderResult orderPreview(PreviewOrderCommand cmd) {
        String userId = cmd.getUserId();
        List<String> productIds = cmd.getProductIds();
        // 验证参数
        boolean validPram = validateOrderParam(userId, productIds, cmd.getCoupons(),
                cmd.getAddressId());
        if (!validPram) {
            log.debug("");
            return null;
        }
        // 获取地址信息

        // 获取商品和优惠券信息
        List<Product> products = productService.getProductsByIds(productIds);
        List<Coupon> coupons = couponService.getAvailableCoupons(userId);
        // 创建订单聚合
        Order order = orderFactory.createOrder(cmd, products, coupons);
        // 缓存预览结果
        log.debug("Order preview created: {}", order);
        return PreviewOrderResult.fromOrder(order);
    }

    public CreateOrderResult submitOrder(CreateOrderCommand cmd) {
        String userId = cmd.getUserId();
        List<String> productIds = cmd.getProductIds();
        boolean validPram = validateOrderParam(userId, productIds, cmd.getCoupons(),
                cmd.getAddressId());
        if (!validPram) {
            log.debug("");
            return null;
        }
        List<Product> products = productService.getProductsByIds(productIds);
        List<Coupon> coupons = couponService.getAvailableCoupons(userId);
        // 锁定库存
        boolean productDeducted = productValidatorService.deductProductStocks(cmd.getProductItems());
        // 库存不足，抛出异常
        if (!productDeducted) {
            throw new IllegalArgumentException("库存不足");
        }
        boolean couponDeducted = productValidatorService.deductCoupons(cmd.getUserId(),
                cmd.getCoupons());
        // 创建订单聚合
        Order order = orderFactory.createOrder(cmd, products, coupons);
        orderDomainService.saveOrderLine(order);
        // 发布订单创建事件
        orderOutboxService.recordEvent(order);
        // TODO: 使用 couponDeducted 结果
        log.debug("Coupon deduction result: {}", couponDeducted);
        return new CreateOrderResult();
    }

    public Object cancelPreview(String orderId) {
        // TODO: 检查订单状态，是否可以取消
        boolean canCancel = cancelDecisionService.canCancel(orderId);
        if (!canCancel) {
            return false;
        }
        // TODO: 生成幂等键
        return UUID.randomUUID();
    }

    public Object cancelOrder(CancelOrderCommand cmd) {
        // TODO: 根据订单状态取消订单
        // TODO: (opt) 发送取消订单请求给商家, 等待商家确认
        // TODO: (opt) 商家确认后，通过确认接口调用取消订单
        // TODO: 发送取消订单事件
        // TODO: 释放库存, 优惠券等
        // TODO: (支付服务) 异步退款
        return "待确认";
    }

    // ==================== 新增的订单主流程接口方法 ====================

    /**
     * 支付成功回调处理
     */
    public void onPaymentSuccess(PaymentSuccessCommand cmd) {
        // 1. 幂等性检查（由网关处理，这里仅记录日志）
        log.info("Processing payment success for order: {}, payType: {}, eventId: {}",
                cmd.getOrderId(), cmd.getPayType(), cmd.getEventId());

        // 2. 加载订单 (load-for-update with version)
        Order order = orderDomainService.loadForUpdate(cmd.getOrderId());
        long expectedVersion = order.getVersion();

        // 3. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
        // CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
        // 临时使用占位符
        CoreFlowStatus currentStatus = CoreFlowStatus.PENDING_PAYMENT;

        // 4. 应用状态机过渡
        boolean isDeposit = false; // 根据 payType 判断，暂时默认为全款支付
        boolean isFinalPayment = true; // 根据订单类型判断，暂时默认为最终支付
        CoreFlowStatus newStatus = orderStateTransitionService.paymentSuccess(currentStatus, isDeposit, isFinalPayment);

        // 5. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
        // order.setStatus(orderStatusTranslator.toLegacy(newStatus));
        orderDomainService.save(order, expectedVersion);

        // 6. 追加状态日志 - 待实现 Order.getId()
        // orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
        //	"Payment success", "system", cmd.getEventId());

        // 7. 记录 Outbox 事件
        orderDomainService.recordOutbox(order, "payment.success", cmd);

        log.info("Payment success processed for order: {}", cmd.getOrderId());
    }

    /**
     * 商家接单
     */
    public void merchantAccept(MerchantAcceptCommand cmd) {
        log.info("Merchant accepting order: {}, operator: {}", cmd.getOrderId(), cmd.getOperatorId());

        // 1. 加载订单 (load-for-update with version)
        Order order = orderDomainService.loadForUpdate(cmd.getOrderId());
        long expectedVersion = order.getVersion();

        // 2. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
        // CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
        // 临时使用占位符
        CoreFlowStatus currentStatus = CoreFlowStatus.PAID_CONFIRMED;

        // 3. 应用状态机过渡
        CoreFlowStatus newStatus = orderStateTransitionService.moveToAwaitingFulfillment(currentStatus);

        // 4. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
        // order.setStatus(orderStatusTranslator.toLegacy(newStatus));
        orderDomainService.save(order, expectedVersion);

        // 5. 追加状态日志 - 待实现 Order.getId()
        // orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
        //	"Merchant accepted", cmd.getOperatorId(), cmd.getRequestId());

        // 6. 记录 Outbox 事件
        orderDomainService.recordOutbox(order, "merchant.accept", cmd);

        log.info("Merchant accept processed for order: {}", cmd.getOrderId());
    }

    /**
     * 商家发货
     */
    public void shipOrder(ShipOrderCommand cmd) {
        log.info("Shipping order: {}, operator: {}, logistics: {}",
                cmd.getOrderId(), cmd.getOperatorId(), cmd.getLogistics().getCompanyName());

        // 1. 加载订单 (load-for-update with version)
        Order order = orderDomainService.loadForUpdate(cmd.getOrderId());
        long expectedVersion = order.getVersion();

        // 2. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
        // CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
        // 临时使用占位符
        CoreFlowStatus currentStatus = CoreFlowStatus.AWAITING_FULFILLMENT;

        // 3. 应用状态机过渡
        CoreFlowStatus newStatus = orderStateTransitionService.startFulfillment(currentStatus);

        // 4. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
        // order.setStatus(orderStatusTranslator.toLegacy(newStatus));
        orderDomainService.save(order, expectedVersion);

        // 5. 追加状态日志 - 待实现 Order.getId()
        // orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
        //	"Order shipped", cmd.getOperatorId(), cmd.getRequestId());

        // 6. 记录 Outbox 事件
        orderDomainService.recordOutbox(order, "order.shipped", cmd);

        log.info("Ship order processed for order: {}", cmd.getOrderId());
    }

    /**
     * 商家同意取消
     */
    public void approveCancelRequest(CancelApproveCommand cmd) {
        // TODO: 实现取消审批通过逻辑
        // TODO: 调用状态机 cancelApproved 方法
        // TODO: 触发退款流程
        log.info("Approving cancel request for order: {}, operator: {}", cmd.getOrderId(), cmd.getOperatorId());
    }

    /**
     * 商家拒绝取消
     */
    public void rejectCancelRequest(CancelRejectCommand cmd) {
        // TODO: 实现取消审批拒绝逻辑
        // TODO: 调用状态机 cancelRejected 方法
        // TODO: 回退到之前状态
        log.info("Rejecting cancel request for order: {}, reason: {}", cmd.getOrderId(), cmd.getReasonCode());
    }

    /**
     * 物流妥投处理
     */
    public void onLogisticsDelivered(DeliveredCommand cmd) {
        log.info("Processing delivery for order: {}, source: {}", cmd.getOrderId(), cmd.getSource());

        // 1. 加载订单 (load-for-update with version)
        Order order = orderDomainService.loadForUpdate(cmd.getOrderId());
        long expectedVersion = order.getVersion();

        // 2. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
        // CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
        // 临时使用占位符
        CoreFlowStatus currentStatus = CoreFlowStatus.FULFILLING;

        // 3. 应用状态机过渡 (启用售后观察期)
        boolean afterSaleWindowOpen = true; // 物流妥投后开启售后观察期
        CoreFlowStatus newStatus = orderStateTransitionService.delivered(currentStatus, afterSaleWindowOpen);

        // 4. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
        // order.setStatus(orderStatusTranslator.toLegacy(newStatus));
        orderDomainService.save(order, expectedVersion);

        // 5. 追加状态日志 - 待实现 Order.getId()
        // orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
        //	"Logistics delivered", "system", cmd.getEventId());

        // 6. 记录 Outbox 事件
        orderDomainService.recordOutbox(order, "logistics.delivered", cmd);

        log.info("Logistics delivery processed for order: {}", cmd.getOrderId());
    }

    /**
     * 用户确认收货
     */
    public void confirmReceipt(ConfirmReceiptCommand cmd) {
        log.info("User confirming receipt for order: {}, user: {}", cmd.getOrderId(), cmd.getUserId());

        // 1. 加载订单 (load-for-update with version)
        Order order = orderDomainService.loadForUpdate(cmd.getOrderId());
        long expectedVersion = order.getVersion();

        // 2. 转换当前状态到 CoreFlowStatus (待实现 - Order 需要添加状态字段)
        // CoreFlowStatus currentStatus = orderStatusTranslator.toCore(order.getCurrentStatus());
        // 临时使用占位符
        CoreFlowStatus currentStatus = CoreFlowStatus.FULFILLING;

        // 3. 应用状态机过渡 (用户确认收货，不开启售后观察期)
        boolean afterSaleWindowOpen = false; // 用户主动确认，直接完成
        CoreFlowStatus deliveredStatus = orderStateTransitionService.delivered(currentStatus, afterSaleWindowOpen);
        CoreFlowStatus newStatus = orderStateTransitionService.completeIfNoAfterSale(deliveredStatus);

        // 4. 持久化 (save with optimistic lock) - 待实现 Order.setStatus
        // order.setStatus(orderStatusTranslator.toLegacy(newStatus));
        orderDomainService.save(order, expectedVersion);

        // 5. 追加状态日志 - 待实现 Order.getId()
        // orderDomainService.appendStatusLog(order.getId(), currentStatus, newStatus,
        //	"User confirmed receipt", cmd.getUserId(), cmd.getRequestId());

        // 6. 记录 Outbox 事件
        orderDomainService.recordOutbox(order, "user.confirm_receipt", cmd);

        log.info("User receipt confirmation processed for order: {}", cmd.getOrderId());
    }

    /**
     * 申请售后
     */
    public String applyAfterSale(AfterSaleApplyCommand cmd) {
        // TODO: 实现售后申请逻辑
        // TODO: 调用状态机 requestAfterSale 方法
        // TODO: 创建售后单
        log.info("Applying after-sale for order: {}, type: {}", cmd.getOrderId(), cmd.getType());
        return UUID.randomUUID().toString(); // 返回售后单ID
    }

    /**
     * 退款成功回调处理
     */
    public void onRefundSuccess(RefundSuccessCommand cmd) {
        // TODO: 实现退款成功处理逻辑
        // TODO: 调用状态机 refundSuccess 方法
        // TODO: 处理优惠券回滚
        log.info("Processing refund success for order: {}, amount: {}", cmd.getOrderId(), cmd.getAmount());
    }

    /**
     * 自动完成订单
     */
//    public void autoComplete(AutoCompleteCommand cmd) {
//        // TODO: 实现自动完成逻辑
//        // TODO: 调用状态机 completeIfNoAfterSale 方法
//        // TODO: 检查是否满足自动完成条件
//        log.info("Auto completing order: {}, grace days: {}", cmd.getOrderId(), cmd.getGraceDays());
//    }

    /**
     * 支付超时自动取消
     */
    public void timeoutCancel(UnpaidTimeoutCancelCommand cmd) {
        // TODO: 实现超时取消逻辑
        // TODO: 调用状态机 cancelRequest -> cancelApproved 方法
        // TODO: 释放库存和优惠券
        log.info("Timeout cancelling order: {}, schedule: {}", cmd.getOrderId(), cmd.getScheduleId());
    }

    /**
     * 转待履约（保障性作业）
     */
    public void moveToAwaitFulfillment(MoveToAwaitFulfillmentCommand cmd) {
        // TODO: 实现转待履约逻辑
        // TODO: 调用状态机 moveToAwaitingFulfillment 方法
        log.info("Moving order to await fulfillment: {}", cmd.getOrderId());
    }

    public boolean validateOrderParam(String userId, List<String> productIds, Set<String> couponIds,
                                      String addressId) {
        // 检查用户是否可以下单
        boolean canOrder = userValidatorService.canUserPlaceOrder(userId, productIds);
        if (!canOrder) {
            throw new IllegalArgumentException("用户无法下单");
        }
        // 检查商品是否有效
        boolean productsValid = productValidatorService.validateProducts(productIds);
        if (!productsValid) {
            throw new IllegalArgumentException("包含无效商品");
        }
        boolean couponValid = productValidatorService.validateCoupons(couponIds);
        if (!couponValid) {
            throw new IllegalArgumentException("包含无效优惠券");
        }
        // 检查收货地址是否有效
        boolean addressValid = userValidatorService.validateAddress(userId, addressId);
        if (!addressValid) {
            throw new IllegalArgumentException("收货地址无效");
        }
        return true;
    }
}
