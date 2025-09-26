package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.model.line.LineItem;
import com.github.spud.tinystore.order.domain.status.*;
import lombok.Data;

import java.util.List;

/**
 * 拆分后的子订单
 * <p>
 * 作为持久化的业务载体，承载店铺维度的履约、费用与地址信息
 * 订单行（LineItem）为权威数据来源
 *
 * @author Spud
 * @date 2025/9/6
 */
@Data
public class SubOrder {

	private String subOrderId;
	private String shopId;
	private List<LineItem> lines;
	private List<ChargeItem> charges;
	private List<DiscountAllocation> discountAllocations;
	private List<CouponAllocation> couponAllocations;
	private Money total;
	private Money payable;
	private Address address;
	private CoreFlowStatus coreFlowStatus;
	private PaymentStatus paymentStatus;
	private FulfillmentStatus fulfillmentStatus;
	private AfterSaleStatus afterSaleStatus;
	private CancellationStatus cancellationStatus;
	private CoreFlowStatus previousCoreFlowStatus;

	public SubOrder(String subOrderId, String shopId, List<LineItem> lines, List<ChargeItem> charges, Address address) {
		this.subOrderId = subOrderId;
		this.shopId = shopId;
		this.lines = lines;
		this.charges = charges;
		this.address = address;
		// 默认状态
		this.coreFlowStatus = CoreFlowStatus.CREATED;
		this.paymentStatus = PaymentStatus.NONE;
		this.fulfillmentStatus = FulfillmentStatus.NONE;
		this.afterSaleStatus = AfterSaleStatus.NONE;
		this.cancellationStatus = CancellationStatus.NONE;
		// TODO: 计算金额
	}

//	/**
//	 * Handle payment success
//	 */
//	public void onPaymentSuccess(Order.PaymentSuccessArgs args) {
//		validateNotTerminal("payment success");
//
//		// Use state machine for transition
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.paymentSuccess(
//			this.coreFlowStatus,
//			args.isDeposit(),
//			args.isFinalPayment()
//		);
//
//		if (newStatus != this.coreFlowStatus) {
//			this.coreFlowStatus = newStatus;
//			this.paymentStatus = determinePaymentStatus(args);
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//
//			addDomainEvent(OrderPaymentSucceededEvent.builder()
//				.orderId(this.orderId)
//				.paymentId(args.getPaymentId())
//				.amount(args.getAmount())
//				.isDeposit(args.isDeposit())
//				.isFinalPayment(args.isFinalPayment())
//				.build());
//		}
//	}
//
//	/**
//	 * Merchant receives order
//	 */
//	public void onMerchantReceive() {
//		validateNotTerminal("merchant receive");
//
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.moveToAwaitingFulfillment(this.coreFlowStatus);
//
//		if (newStatus != this.coreFlowStatus) {
//			this.coreFlowStatus = newStatus;
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//		}
//	}
//
//	/**
//	 * Ship order
//	 */
//	public void onShip(Order.ShipArgs args) {
//		validateNotTerminal("ship");
//
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.startFulfillment(this.coreFlowStatus);
//
//		if (newStatus != this.coreFlowStatus) {
//			this.coreFlowStatus = newStatus;
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//
//			addDomainEvent(SubOrderShippedEvent.builder()
//				.orderId(this.orderId)
//				.subOrderId(args.getSubOrderId())
//				.shipmentInfo(args.getShipmentInfo())
//				.build());
//		}
//	}
//
//	/**
//	 * Handle delivery
//	 */
//	public void onDelivered(Order.DeliveredArgs args) {
//		validateNotTerminal("delivery");
//
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.delivered(this.coreFlowStatus, args.isAfterSaleWindowOpen());
//
//		if (newStatus != this.coreFlowStatus) {
//			this.coreFlowStatus = newStatus;
//			this.afterSaleWindowOpen = args.isAfterSaleWindowOpen();
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//		}
//	}
//
//	/**
//	 * Auto complete order
//	 */
//	public void onAutoComplete() {
//		validateNotTerminal("auto complete");
//
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.completeIfNoAfterSale(this.coreFlowStatus);
//
//		if (newStatus != this.coreFlowStatus) {
//			this.coreFlowStatus = newStatus;
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//
//			addDomainEvent(OrderCompletedEvent.builder()
//				.orderId(this.orderId)
//				.completedAt(LocalDateTime.now())
//				.build());
//		}
//	}
//
//	/**
//	 * Request cancel
//	 */
//	public void requestCancel() {
//		validateNotTerminal("request cancel");
//
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.cancelRequest(this.coreFlowStatus);
//
//		if (newStatus != this.coreFlowStatus) {
//			this.previousCoreFlowStatus = this.coreFlowStatus; // Save for potential rollback
//			this.coreFlowStatus = newStatus;
//			this.cancellationStatus = CancellationStatus.REQUESTED;
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//		}
//	}
//
//	/**
//	 * Approve cancel
//	 */
//	public void approveCancel() {
//		if (this.coreFlowStatus != CoreFlowStatus.CANCELLING) {
//			throw new OrderTransitionNotAllowedException(
//				this.coreFlowStatus,
//				"approve cancel",
//				OrderTransitionNotAllowedException.ReasonCode.ILLEGAL_STATE
//			);
//		}
//
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.cancelApproved(this.coreFlowStatus);
//
//		this.coreFlowStatus = newStatus;
//		this.cancellationStatus = CancellationStatus.CANCELLED;
//		this.previousCoreFlowStatus = null; // Clear rollback state
//		this.updatedAt = LocalDateTime.now();
//		this.version++;
//
//		addDomainEvent(OrderCancelledEvent.builder()
//			.orderId(this.orderId)
//			.cancelledAt(LocalDateTime.now())
//			.build());
//	}
//
//	/**
//	 * Reject cancel
//	 */
//	public void rejectCancel() {
//		if (this.coreFlowStatus != CoreFlowStatus.CANCELLING) {
//			throw new OrderTransitionNotAllowedException(
//				this.coreFlowStatus,
//				"reject cancel",
//				OrderTransitionNotAllowedException.ReasonCode.ILLEGAL_STATE
//			);
//		}
//
//		if (this.previousCoreFlowStatus == null) {
//			throw new OrderDomainException("Cannot reject cancel: no previous state to rollback to");
//		}
//
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.cancelRejected(this.coreFlowStatus, this.previousCoreFlowStatus);
//
//		this.coreFlowStatus = newStatus;
//		this.cancellationStatus = CancellationStatus.NONE;
//		this.previousCoreFlowStatus = null; // Clear rollback state
//		this.updatedAt = LocalDateTime.now();
//		this.version++;
//	}
//
//	/**
//	 * Request after sale
//	 */
//	public void requestAfterSale() {
//		if (!this.afterSaleWindowOpen && this.coreFlowStatus == CoreFlowStatus.FULFILLING) {
//			throw new OrderTransitionNotAllowedException(
//				this.coreFlowStatus,
//				"request after sale",
//				OrderTransitionNotAllowedException.ReasonCode.WINDOW_CLOSED
//			);
//		}
//
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.requestAfterSale(this.coreFlowStatus);
//
//		if (newStatus != this.coreFlowStatus) {
//			this.coreFlowStatus = newStatus;
//			this.afterSaleStatus = AfterSaleStatus.APPLY_SUBMITTED;
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//
//			addDomainEvent(RefundRequestedEvent.builder()
//				.orderId(this.orderId)
//				.requestedAt(LocalDateTime.now())
//				.build());
//		}
//	}
//
//	/**
//	 * Handle refund success
//	 */
//	public void onRefundSuccess() {
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.refundSuccess(this.coreFlowStatus);
//
//		if (newStatus != this.coreFlowStatus) {
//			this.coreFlowStatus = newStatus;
//			this.afterSaleStatus = AfterSaleStatus.COMPLETED;
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//
//			addDomainEvent(RefundCompletedEvent.builder()
//				.orderId(this.orderId)
//				.refundedAt(LocalDateTime.now())
//				.build());
//		}
//	}
//
//	/**
//	 * Handle exchange completed
//	 */
//	public void onExchangeCompleted() {
//		OrderStateTransitionService stateService = new OrderStateTransitionService();
//		CoreFlowStatus newStatus = stateService.exchangeCompleted(this.coreFlowStatus);
//
//		if (newStatus != this.coreFlowStatus) {
//			this.coreFlowStatus = newStatus;
//			this.afterSaleStatus = AfterSaleStatus.COMPLETED;
//			this.updatedAt = LocalDateTime.now();
//			this.version++;
//		}
//	}
//
//	/**
//	 * Reprice order (limited scenarios)
//	 */
//	public void reprice(Order.RepriceArgs args) {
//		validateNotTerminal("reprice");
//
//		if (this.coreFlowStatus != CoreFlowStatus.CREATED && this.coreFlowStatus != CoreFlowStatus.PENDING_PAYMENT) {
//			throw new OrderTransitionNotAllowedException(
//				this.coreFlowStatus,
//				"reprice",
//				OrderTransitionNotAllowedException.ReasonCode.ILLEGAL_STATE
//			);
//		}
//
//		// Validate amount conservation
//		validateAmountConservation(args.getNewPricingSummary());
//
//		this.pricingSummary = args.getNewPricingSummary();
//		this.couponAllocations = args.getNewCouponAllocations();
//		this.discountAllocations = args.getNewDiscountAllocations();
//		this.calculated = true;
//		this.updatedAt = LocalDateTime.now();
//		this.version++;
//	}
//
//	/**
//	 * Pull and clear domain events
//	 */
//	public List<OrderDomainEvent> pullDomainEvents() {
//		List<OrderDomainEvent> events = new ArrayList<>(this.orderDomainEvents);
//		this.orderDomainEvents.clear();
//		return events;
//	}
//
//	private void validateNotTerminal(String operation) {
//		if (TerminalStateChecker.isTerminal(this.coreFlowStatus)) {
//			throw new OrderTransitionNotAllowedException(
//				this.coreFlowStatus,
//				operation,
//				OrderTransitionNotAllowedException.ReasonCode.TERMINAL_STATE
//			);
//		}
//	}
//
//	private static void validateCreateArgs(Order.CreateOrderArgs args) {
//		if (args.getBuyer() == null) {
//			throw new OrderDomainException("Buyer is required", "MISSING_BUYER");
//		}
//		if (args.getPricingSummary() == null) {
//			throw new OrderDomainException("Pricing summary is required", "MISSING_PRICING");
//		}
//		if (args.getSubOrders() == null || args.getSubOrders().isEmpty()) {
//			throw new OrderDomainException("At least one sub-order is required", "MISSING_SUB_ORDERS");
//		}
//	}
//
//	private PaymentStatus determinePaymentStatus(Order.PaymentSuccessArgs args) {
//		if (args.isDeposit()) {
//			return PaymentStatus.DEPOSIT_PAID;
//		} else if (args.isFinalPayment()) {
//			return PaymentStatus.PAYMENT_SUCCESS;
//		} else {
//			return PaymentStatus.PAYMENT_SUCCESS;
//		}
//	}
//
//	private void validateAmountConservation(PricingSummary newPricing) {
//		if (this.pricingSummary != null && newPricing != null) {
//			Money oldTotal = this.pricingSummary.total();
//			Money newTotal = newPricing.total();
//			long difference = Math.abs(oldTotal.amount() - newTotal.amount());
//
//			// Allow small differences due to rounding (1 cent)
//			if (difference > 1) {
//				throw new OrderDomainException(
//					String.format("Amount conservation violated: old=%s, new=%s, diff=%d",
//						oldTotal, newTotal, difference),
//					"AMOUNT_CONSERVATION_VIOLATION"
//				);
//			}
//		}
//	}
//
//
//	/**
//	 * 重新计算子订单金额
//	 *
//	 * @return 重算后的应付金额
//	 */
//	public Money recompute() {
//		// 计算行总额
//		Money linesTotal = lines.stream()
//			.map(LineItem::getLinePayable)
//			.reduce(Money.zero(), Money::add);
//
//		// 加上额外费用
//		Money chargesTotal = charges.stream()
//			.map(ChargeItem::amount)
//			.reduce(Money.zero(), Money::add);
//
//		// 减去子订单级折扣
//		Money discountsTotal = discountAllocations.stream()
//			.map(DiscountAllocation::amount)
//			.reduce(Money.zero(), Money::add);
//
//		Money couponsTotal = couponAllocations.stream()
//			.map(CouponAllocation::amount)
//			.reduce(Money.zero(), Money::add);
//
//		Money computed = linesTotal.add(chargesTotal).subtract(discountsTotal).subtract(couponsTotal);
//
//		if (!computed.nonNegative()) {
//			throw new IllegalStateException("SubOrder payable cannot be negative: " + computed);
//		}
//
//		return computed;
//	}

	/**
	 * 计算小计（不含费用和折扣）
	 *
	 * @return 小计金额
	 */
	public Money computeSubtotal() {
		return lines.stream()
			.map(LineItem::getLineTotal)
			.reduce(Money.zero(), com.github.spud.tinystore.order.domain.model.Money::add);
	}

	/**
	 * 应用折扣分摊到各行
	 */
	public void applyDiscountAllocations() {
		// TODO: 实现折扣分摊逻辑
		// 按行金额比例分摊或其他策略
	}

	/**
	 * 标记部分行已发货
	 *
	 * @param partialLineIds 部分发货的行ID列表
	 */
	public void markShipped(List<String> partialLineIds) {
		// TODO: 实现部分发货逻辑
		// 更新履约状态，可能需要与库存域协调
	}

	/**
	 * 标记已签收
	 */
	public void markDelivered() {
		// TODO: 实现签收逻辑
		// 更新履约状态为已完成
	}

	/**
	 * 检查是否已完全履约
	 *
	 * @return true 如果所有行都已履约完成
	 */
	public boolean isFulfilled() {
		// TODO: 基于库存域的投影/事件判断履约完成状态
		return fulfillmentStatus == com.github.spud.tinystore.order.domain.status.FulfillmentStatus.DELIVERED;
	}
}