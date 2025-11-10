package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderPaidEvent;
import com.github.spud.tinystore.order.domain.event.OrderStatusChangedEvent;
import com.github.spud.tinystore.order.domain.statemachine.enums.OrderMainStatus;
import com.github.spud.tinystore.order.domain.statemachine.enums.OrderSubStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Order Aggregate Root - Rich Domain Model Encapsulates order business logic, invariants, and state
 * transitions
 *
 * @author Spud
 * @date 2025/9/1
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class OrderAggregate {

	private String id;                 // 数据库主键
	private String orderNo;            // 订单号（业务唯一）
	private String tenantId;           // 租户ID
	private String userId;             // 用户ID

	// 状态管理
	private OrderMainStatus mainStatus;
	private OrderSubStatus subStatus;
	private Integer version;

	// 领域事件队列
	@Builder.Default
	private List<OrderDomainEvent> domainEvents = new ArrayList<>();

	// 业务字段
	private Buyer buyer;
	private List<OrderItem> orderItems;
	private List<Coupon> coupons;
	private List<Discount> discounts;

	private List<CouponAllocation> couponAllocations;
	private List<DiscountAllocation> discountAllocations;
	private PricingSummary pricingSummary;
	private List<ReservationRef> reservations;
	// Enhanced fields for rich aggregate
	private String addressId;
	private Map<String, String> attributes; // Flexible extension
	private boolean calculated;

	// 时间戳
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	public OrderAggregate(Buyer buyer, List<OrderItem> orderItems, List<Coupon> coupons,
		List<Discount> discounts, String addressId) {
		this.buyer = buyer;
		this.orderItems = orderItems;
		this.coupons = coupons;
		this.discounts = discounts;
		this.addressId = addressId;
		this.couponAllocations = new ArrayList<>();
		this.discountAllocations = new ArrayList<>();
		this.reservations = new ArrayList<>();
		this.domainEvents = new ArrayList<>();
		this.version = 0;
	}

	/**
	 * 处理支付成功事件
	 *
	 * @param args 支付成功参数
	 */
	public void onPaymentSuccess(PaymentSuccessArgs args) {
		// 添加支付成功事件
		OrderPaidEvent paidEvent = new OrderPaidEvent(
			this.orderNo,
			args.getPaymentId(),
			args.getAmount(),
			args.isDeposit(),
			args.isFinalPayment()
		);
		this.domainEvents.add(paidEvent);

		// 记录状态变更事件
		String fromStatus = formatStatus(this.mainStatus, this.subStatus);

		// 状态变更将由状态机处理，这里预留
		// this.mainStatus = MainOrderStatus.PAID;
		// this.subStatus = SubOrderStatus.PENDING_SHIP;

		String toStatus = formatStatus(OrderMainStatus.PAID, OrderSubStatus.PENDING_SHIP);
		OrderStatusChangedEvent statusEvent = new OrderStatusChangedEvent(
			this.orderNo,
			fromStatus,
			toStatus,
			"Payment succeeded",
			"SYSTEM",
			"payment-service"
		);
		this.domainEvents.add(statusEvent);

		this.updatedAt = OffsetDateTime.now();
		this.version++;
	}

	/**
	 * 拉取并清空领域事件
	 *
	 * @return 领域事件列表
	 */
	public List<OrderDomainEvent> pullDomainEvents() {
		List<OrderDomainEvent> events = new ArrayList<>(this.domainEvents);
		this.domainEvents.clear();
		return events;
	}

	/**
	 * 添加领域事件
	 *
	 * @param event 领域事件
	 */
	public void addDomainEvent(OrderDomainEvent event) {
		this.domainEvents.add(event);
	}

	/**
	 * 获取当前状态字符串表示
	 *
	 * @return 状态字符串 (主状态:子状态)
	 */
	public String getCurrentStatus() {
		return formatStatus(this.mainStatus, this.subStatus);
	}

	/**
	 * 设置状态（由状态机调用）
	 *
	 * @param mainStatus 主状态
	 * @param subStatus  子状态
	 */
	public void updateStatus(OrderMainStatus mainStatus, OrderSubStatus subStatus) {
		String fromStatus = getCurrentStatus();
		this.mainStatus = mainStatus;
		this.subStatus = subStatus;
		this.updatedAt = OffsetDateTime.now();
		this.version++;

		// 添加状态变更事件
		OrderStatusChangedEvent statusEvent = new OrderStatusChangedEvent(
			this.orderNo,
			fromStatus,
			getCurrentStatus(),
			"State machine transition",
			"SYSTEM",
			"state-machine"
		);
		this.addDomainEvent(statusEvent);
	}

	public void computePricing() {
		// TODO: 实现定价计算逻辑
	}

	/**
	 * 格式化状态字符串
	 */
	private String formatStatus(OrderMainStatus mainStatus, OrderSubStatus subStatus) {
		if (mainStatus == null) {
			return "UNKNOWN";
		}
		if (subStatus == null) {
			return mainStatus.getCode();
		}
		return mainStatus.getCode() + ":" + subStatus.getCode();
	}

	// Argument classes (inner classes for now, can be moved to separate files)
	@Data
	@Builder
	public static class CreateOrderArgs {

		private Buyer buyer;
		private List<OrderItem> orderItems;
		private List<Coupon> coupons;
		private List<Discount> discounts;
		private Address address;

		public void valid() {
			if (buyer == null) {
				throw new IllegalArgumentException("Buyer cannot be null");
			}
			if (orderItems == null || orderItems.isEmpty()) {
				throw new IllegalArgumentException("SubOrders cannot be null or empty");
			}
			if (address == null) {
				throw new IllegalArgumentException("Address cannot be null");
			}
		}

		public static CreateOrderArgsBuilder builder() {
			return new InternalBuilder();
		}

		public static class InternalBuilder extends CreateOrderArgsBuilder {

			@Override
			public CreateOrderArgs build() {
				CreateOrderArgs args = super.build();
				args.valid();
				return args;
			}
		}

	}

	@Data
	@Builder
	public static class PaymentSuccessArgs {

		private String paymentId;
		private Money amount;
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

		// TODO: 
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
