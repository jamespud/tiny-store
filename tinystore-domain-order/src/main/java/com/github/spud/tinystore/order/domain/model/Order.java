package com.github.spud.tinystore.order.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.spud.tinystore.order.domain.event.*;
import com.github.spud.tinystore.order.domain.exception.OrderDomainException;
import com.github.spud.tinystore.order.domain.exception.OrderTransitionNotAllowedException;
import com.github.spud.tinystore.order.domain.status.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order Aggregate Root - Rich Domain Model
 * Encapsulates order business logic, invariants, and state transitions
 * 
 * @author Spud
 * @date 2025/9/1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    // Original fields
    private Buyer buyer;
    private List<SubOrder> subOrders;
    
    /**
     * @deprecated 此字段已废弃，不再作为权威数据来源
     * 商品明细以 {@code subOrders -> lines} 为准
     * 此字段仅用于只读派生视图，计划在后续版本中移除
     */
    @Deprecated(since = "2025-09-22", forRemoval = true)
    private Map<Product, Integer> products;
    
    private List<Coupon> coupons;
    private List<Discount> discounts;
    private List<CouponAllocation> couponAllocations;
    private List<DiscountAllocation> discountAllocations;
    private PricingSummary pricingSummary;
    private List<ReservationRef> reservations;
    private Integer version;
    private List<OrderDomainEvent> orderDomainEvents;
    private String deviceId;
    private boolean calculated;
    
    /**
     * 地址信息（非权威）
     * 仅用于展示和下单时的地址快照
     * 权威地址信息存储在各 SubOrder 中，用于实际履约
     */
    private Address address;

    // Enhanced fields for rich aggregate
    private String orderId;
    private CoreFlowStatus coreFlowStatus;
    private PaymentStatus paymentStatus;
    private CancellationStatus cancellationStatus;
    private AfterSaleStatus afterSaleStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private CoreFlowStatus previousCoreFlowStatus; // For cancel rejection rollback
    private boolean afterSaleWindowOpen;
    private Map<String, String> attributes; // Flexible extension

    /**
     * Create a new order
     */
    public static Order create(CreateOrderArgs args) {
        validateCreateArgs(args);
        
        String orderId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        
        Order order = Order.builder()
                .orderId(orderId)
                .buyer(args.getBuyer())
                .subOrders(args.getSubOrders())
                // Deprecated: .products(args.getProducts()) - no longer set, derived from subOrders
                .coupons(args.getCoupons())
                .discounts(args.getDiscounts())
                .couponAllocations(args.getCouponAllocations())
                .discountAllocations(args.getDiscountAllocations())
                .pricingSummary(args.getPricingSummary())
                .reservations(args.getReservations())
                .address(args.getAddress()) // Non-authoritative display address
                .deviceId(args.getDeviceId())
                .coreFlowStatus(CoreFlowStatus.CREATED)
                .paymentStatus(PaymentStatus.NONE)
                .cancellationStatus(CancellationStatus.NONE)
                .afterSaleStatus(AfterSaleStatus.NONE)
                .createdAt(now)
                .updatedAt(now)
                .version(0)
                .calculated(true)
                .afterSaleWindowOpen(false)
                .orderDomainEvents(new ArrayList<>())
                .build();

        // Add creation event
        order.addDomainEvent(OrderCreatedEvent.builder()
                .orderId(orderId)
                .buyerId(args.getBuyer().userId())
                .totalAmount(args.getPricingSummary().total())
                .createdAt(now)
                .build());

        return order;
    }

    /**
     * Handle payment success
     */
    public void onPaymentSuccess(PaymentSuccessArgs args) {
        validateNotTerminal("payment success");
        
        // Use state machine for transition
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.paymentSuccess(
                this.coreFlowStatus, 
                args.isDeposit(), 
                args.isFinalPayment()
        );
        
        if (newStatus != this.coreFlowStatus) {
            this.coreFlowStatus = newStatus;
            this.paymentStatus = determinePaymentStatus(args);
            this.updatedAt = LocalDateTime.now();
            this.version++;
            
            addDomainEvent(OrderPaymentSucceededEvent.builder()
                    .orderId(this.orderId)
                    .paymentId(args.getPaymentId())
                    .amount(args.getAmount())
                    .isDeposit(args.isDeposit())
                    .isFinalPayment(args.isFinalPayment())
                    .build());
        }
    }

    /**
     * Merchant receives order
     */
    public void onMerchantReceive() {
        validateNotTerminal("merchant receive");
        
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.moveToAwaitingFulfillment(this.coreFlowStatus);
        
        if (newStatus != this.coreFlowStatus) {
            this.coreFlowStatus = newStatus;
            this.updatedAt = LocalDateTime.now();
            this.version++;
        }
    }

    /**
     * Ship order
     */
    public void onShip(ShipArgs args) {
        validateNotTerminal("ship");
        
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.startFulfillment(this.coreFlowStatus);
        
        if (newStatus != this.coreFlowStatus) {
            this.coreFlowStatus = newStatus;
            this.updatedAt = LocalDateTime.now();
            this.version++;
            
            addDomainEvent(SubOrderShippedEvent.builder()
                    .orderId(this.orderId)
                    .subOrderId(args.getSubOrderId())
                    .shipmentInfo(args.getShipmentInfo())
                    .build());
        }
    }

    /**
     * Handle delivery
     */
    public void onDelivered(DeliveredArgs args) {
        validateNotTerminal("delivery");
        
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.delivered(this.coreFlowStatus, args.isAfterSaleWindowOpen());
        
        if (newStatus != this.coreFlowStatus) {
            this.coreFlowStatus = newStatus;
            this.afterSaleWindowOpen = args.isAfterSaleWindowOpen();
            this.updatedAt = LocalDateTime.now();
            this.version++;
        }
    }

    /**
     * Auto complete order
     */
    public void onAutoComplete() {
        validateNotTerminal("auto complete");
        
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.completeIfNoAfterSale(this.coreFlowStatus);
        
        if (newStatus != this.coreFlowStatus) {
            this.coreFlowStatus = newStatus;
            this.updatedAt = LocalDateTime.now();
            this.version++;
            
            addDomainEvent(OrderCompletedEvent.builder()
                    .orderId(this.orderId)
                    .completedAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * Request cancel
     */
    public void requestCancel() {
        validateNotTerminal("request cancel");
        
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.cancelRequest(this.coreFlowStatus);
        
        if (newStatus != this.coreFlowStatus) {
            this.previousCoreFlowStatus = this.coreFlowStatus; // Save for potential rollback
            this.coreFlowStatus = newStatus;
            this.cancellationStatus = CancellationStatus.REQUESTED;
            this.updatedAt = LocalDateTime.now();
            this.version++;
        }
    }

    /**
     * Approve cancel
     */
    public void approveCancel() {
        if (this.coreFlowStatus != CoreFlowStatus.CANCELLING) {
            throw new OrderTransitionNotAllowedException(
                    this.coreFlowStatus, 
                    "approve cancel", 
                    OrderTransitionNotAllowedException.ReasonCode.ILLEGAL_STATE
            );
        }
        
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.cancelApproved(this.coreFlowStatus);
        
        this.coreFlowStatus = newStatus;
        this.cancellationStatus = CancellationStatus.CANCELLED;
        this.previousCoreFlowStatus = null; // Clear rollback state
        this.updatedAt = LocalDateTime.now();
        this.version++;
        
        addDomainEvent(OrderCancelledEvent.builder()
                .orderId(this.orderId)
                .cancelledAt(LocalDateTime.now())
                .build());
    }

    /**
     * Reject cancel
     */
    public void rejectCancel() {
        if (this.coreFlowStatus != CoreFlowStatus.CANCELLING) {
            throw new OrderTransitionNotAllowedException(
                    this.coreFlowStatus, 
                    "reject cancel", 
                    OrderTransitionNotAllowedException.ReasonCode.ILLEGAL_STATE
            );
        }
        
        if (this.previousCoreFlowStatus == null) {
            throw new OrderDomainException("Cannot reject cancel: no previous state to rollback to");
        }
        
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.cancelRejected(this.coreFlowStatus, this.previousCoreFlowStatus);
        
        this.coreFlowStatus = newStatus;
        this.cancellationStatus = CancellationStatus.NONE;
        this.previousCoreFlowStatus = null; // Clear rollback state
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    /**
     * Request after sale
     */
    public void requestAfterSale() {
        if (!this.afterSaleWindowOpen && this.coreFlowStatus == CoreFlowStatus.FULFILLING) {
            throw new OrderTransitionNotAllowedException(
                    this.coreFlowStatus, 
                    "request after sale", 
                    OrderTransitionNotAllowedException.ReasonCode.WINDOW_CLOSED
            );
        }
        
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.requestAfterSale(this.coreFlowStatus);
        
        if (newStatus != this.coreFlowStatus) {
            this.coreFlowStatus = newStatus;
            this.afterSaleStatus = AfterSaleStatus.APPLY_SUBMITTED;
            this.updatedAt = LocalDateTime.now();
            this.version++;
            
            addDomainEvent(RefundRequestedEvent.builder()
                    .orderId(this.orderId)
                    .requestedAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * Handle refund success
     */
    public void onRefundSuccess() {
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.refundSuccess(this.coreFlowStatus);
        
        if (newStatus != this.coreFlowStatus) {
            this.coreFlowStatus = newStatus;
            this.afterSaleStatus = AfterSaleStatus.COMPLETED;
            this.updatedAt = LocalDateTime.now();
            this.version++;
            
            addDomainEvent(RefundCompletedEvent.builder()
                    .orderId(this.orderId)
                    .refundedAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * Handle exchange completed
     */
    public void onExchangeCompleted() {
        OrderStateTransitionService stateService = new OrderStateTransitionService();
        CoreFlowStatus newStatus = stateService.exchangeCompleted(this.coreFlowStatus);
        
        if (newStatus != this.coreFlowStatus) {
            this.coreFlowStatus = newStatus;
            this.afterSaleStatus = AfterSaleStatus.COMPLETED;
            this.updatedAt = LocalDateTime.now();
            this.version++;
        }
    }

    /**
     * Reprice order (limited scenarios)
     */
    public void reprice(RepriceArgs args) {
        validateNotTerminal("reprice");
        
        if (this.coreFlowStatus != CoreFlowStatus.CREATED && this.coreFlowStatus != CoreFlowStatus.PENDING_PAYMENT) {
            throw new OrderTransitionNotAllowedException(
                    this.coreFlowStatus, 
                    "reprice", 
                    OrderTransitionNotAllowedException.ReasonCode.ILLEGAL_STATE
            );
        }
        
        // Validate amount conservation
        validateAmountConservation(args.getNewPricingSummary());
        
        this.pricingSummary = args.getNewPricingSummary();
        this.couponAllocations = args.getNewCouponAllocations();
        this.discountAllocations = args.getNewDiscountAllocations();
        this.calculated = true;
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    /**
     * Pull and clear domain events
     */
    public List<OrderDomainEvent> pullDomainEvents() {
        List<OrderDomainEvent> events = new ArrayList<>(this.orderDomainEvents);
        this.orderDomainEvents.clear();
        return events;
    }

    // Private helper methods
    private void addDomainEvent(OrderDomainEvent event) {
        if (this.orderDomainEvents == null) {
            this.orderDomainEvents = new ArrayList<>();
        }
        this.orderDomainEvents.add(event);
    }

    private void validateNotTerminal(String operation) {
        if (TerminalStateChecker.isTerminal(this.coreFlowStatus)) {
            throw new OrderTransitionNotAllowedException(
                    this.coreFlowStatus, 
                    operation, 
                    OrderTransitionNotAllowedException.ReasonCode.TERMINAL_STATE
            );
        }
    }

    private static void validateCreateArgs(CreateOrderArgs args) {
        if (args.getBuyer() == null) {
            throw new OrderDomainException("Buyer is required", "MISSING_BUYER");
        }
        if (args.getPricingSummary() == null) {
            throw new OrderDomainException("Pricing summary is required", "MISSING_PRICING");
        }
        if (args.getSubOrders() == null || args.getSubOrders().isEmpty()) {
            throw new OrderDomainException("At least one sub-order is required", "MISSING_SUB_ORDERS");
        }
    }

    private PaymentStatus determinePaymentStatus(PaymentSuccessArgs args) {
        if (args.isDeposit()) {
            return PaymentStatus.DEPOSIT_PAID;
        } else if (args.isFinalPayment()) {
            return PaymentStatus.PAYMENT_SUCCESS;
        } else {
            return PaymentStatus.PAYMENT_SUCCESS;
        }
    }

    private void validateAmountConservation(PricingSummary newPricing) {
        if (this.pricingSummary != null && newPricing != null) {
            Money oldTotal = this.pricingSummary.total();
            Money newTotal = newPricing.total();
            long difference = Math.abs(oldTotal.amount() - newTotal.amount());
            
            // Allow small differences due to rounding (1 cent)
            if (difference > 1) {
                throw new OrderDomainException(
                        String.format("Amount conservation violated: old=%s, new=%s, diff=%d", 
                                oldTotal, newTotal, difference),
                        "AMOUNT_CONSERVATION_VIOLATION"
                );
            }
        }
    }

    // Argument classes (inner classes for now, can be moved to separate files)
    @Data
    @Builder
    public static class CreateOrderArgs {
        private Buyer buyer;
        private List<SubOrder> subOrders;
        
        /**
         * @deprecated 此字段已废弃，不再使用
         * 商品信息应通过 subOrders -> lines 传递
         */
        @Deprecated(since = "2025-09-22", forRemoval = true)
        private Map<Product, Integer> products;
        
        private List<Coupon> coupons;
        private List<Discount> discounts;
        private List<CouponAllocation> couponAllocations;
        private List<DiscountAllocation> discountAllocations;
        private PricingSummary pricingSummary;
        private List<ReservationRef> reservations;
        private Address address;
        private String deviceId;
    }

    @Data
    @Builder
    public static class PaymentSuccessArgs {
        private String paymentId;
        private BigDecimal amount;
        private boolean isDeposit;
        private boolean isFinalPayment;
    }

    @Data
    @Builder
    public static class ShipArgs {
        private String subOrderId;
        private String shipmentInfo;
    }

    @Data
    @Builder
    public static class DeliveredArgs {
        private String shipmentInfo;
        private boolean afterSaleWindowOpen;
    }

    @Data
    @Builder
    public static class RepriceArgs {
        private PricingSummary newPricingSummary;
        private List<CouponAllocation> newCouponAllocations;
        private List<DiscountAllocation> newDiscountAllocations;
    }
}


