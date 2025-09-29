package com.github.spud.tinystore.order.domain.model;

import com.github.spud.tinystore.order.domain.event.DomainEvent;
import com.github.spud.tinystore.order.domain.event.OrderPaidEvent;
import com.github.spud.tinystore.order.domain.event.OrderStatusChangedEvent;
import com.github.spud.tinystore.order.infrastructure.statemachine.enums.OrderMainStatus;
import com.github.spud.tinystore.order.infrastructure.statemachine.enums.OrderSubStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 订单聚合根 - DDD 增强版
 * 集成状态机、领域事件、审计跟踪等功能
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class OrderAggregateEnhanced {

	// ============= 基础标识 =============
	/**
	 * 订单ID (内部主键)
	 */
	private Long orderId;

	/**
	 * 订单号 (业务标识)
	 */
	private String orderNo;

	/**
	 * 用户ID
	 */
	private String userId;

	/**
	 * 商户ID
	 */
	private String merchantId;

	// ============= 状态管理 =============
	/**
	 * 主状态
	 */
	private OrderMainStatus mainStatus = OrderMainStatus.PENDING_PAYMENT;

	/**
	 * 子状态
	 */
	private OrderSubStatus subStatus = OrderSubStatus.PRE_CREATED;

	/**
	 * 乐观锁版本号
	 */
	private Long version = 0L;

	// ============= 业务数据 =============
	/**
	 * 订单总金额
	 */
	private BigDecimal totalAmount;

	/**
	 * 支付金额
	 */
	private BigDecimal payAmount;

	/**
	 * 支付方式
	 */
	private String paymentMethod;

	/**
	 * 支付流水号
	 */
	private String paymentTransactionId;

	// ============= 时间戳 =============
	/**
	 * 订单创建时间
	 */
	private OffsetDateTime createdAt = OffsetDateTime.now();

	/**
	 * 订单更新时间
	 */
	private OffsetDateTime updatedAt = OffsetDateTime.now();

	/**
	 * 支付时间
	 */
	private OffsetDateTime paidAt;

	/**
	 * 发货时间
	 */
	private OffsetDateTime shippedAt;

	/**
	 * 完成时间
	 */
	private OffsetDateTime completedAt;

	// ============= 领域事件 =============
	/**
	 * 领域事件列表 (未发布的事件)
	 */
	private List<DomainEvent> domainEvents = new ArrayList<>();

	// ============= 构造函数 =============
	public OrderAggregateEnhanced() {
	}

	public OrderAggregateEnhanced(String orderNo, String userId, String merchantId,
	                              BigDecimal totalAmount) {
		this.orderNo = orderNo;
		this.userId = userId;
		this.merchantId = merchantId;
		this.totalAmount = totalAmount;
		this.mainStatus = OrderMainStatus.PENDING_PAYMENT;
		this.subStatus = OrderSubStatus.PRE_CREATED;
	}

	// ============= 业务行为方法 =============

	/**
	 * 支付成功处理
	 *
	 * @param paymentTransactionId 支付流水号
	 * @param paymentMethod        支付方式
	 * @param actualPayAmount      实际支付金额
	 */
	public void onPaymentSuccess(String paymentTransactionId,
	                             String paymentMethod,
	                             BigDecimal actualPayAmount) {
		// 业务规则验证
		if (this.mainStatus != OrderMainStatus.PENDING_PAYMENT) {
			throw new IllegalStateException(
				String.format("订单状态不允许支付: orderNo=%s, currentStatus=%s",
					orderNo, mainStatus));
		}

		if (actualPayAmount.compareTo(this.totalAmount) != 0) {
			log.warn("支付金额与订单金额不一致: orderNo={}, totalAmount={}, payAmount={}",
				orderNo, totalAmount, actualPayAmount);
		}

		// 更新订单状态和数据
		OrderMainStatus oldStatus = this.mainStatus;
		OrderSubStatus oldSubStatus = this.subStatus;

		this.mainStatus = OrderMainStatus.PAID;
		this.subStatus = OrderSubStatus.PAYMENT_CONFIRMED;
		this.paymentTransactionId = paymentTransactionId;
		this.paymentMethod = paymentMethod;
		this.payAmount = actualPayAmount;
		this.paidAt = OffsetDateTime.now();
		this.updatedAt = OffsetDateTime.now();
		this.version++;

		// 发布领域事件
		OrderPaidEvent paidEvent = new OrderPaidEvent(
			UUID.randomUUID(),
			this.orderNo,
			OffsetDateTime.now(),
			MDC.get("traceId"),
			new OrderPaidEvent.PaymentDetails(
				paymentTransactionId,
				paymentMethod,
				actualPayAmount,
				this.userId,
				this.merchantId
			)
		);
		addDomainEvent(paidEvent);

		// 发布状态变更事件
		OrderStatusChangedEvent statusEvent = new OrderStatusChangedEvent(
			UUID.randomUUID(),
			this.orderNo,
			OffsetDateTime.now(),
			MDC.get("traceId"),
			new OrderStatusChangedEvent.StatusChangeDetails(
				oldStatus.name(),
				this.mainStatus.name(),
				oldSubStatus.name(),
				this.subStatus.name(),
				"SYSTEM",
				"payment-service",
				"支付成功"
			)
		);
		addDomainEvent(statusEvent);

		log.info("订单支付成功: orderNo={}, paymentTransactionId={}, amount={}",
			orderNo, paymentTransactionId, actualPayAmount);
	}

	/**
	 * 开始履约
	 */
	public void startFulfillment() {
		validateStatusTransition(OrderMainStatus.PAID, "开始履约");

		OrderMainStatus oldStatus = this.mainStatus;
		OrderSubStatus oldSubStatus = this.subStatus;

		this.mainStatus = OrderMainStatus.FULFILLING;
		this.subStatus = OrderSubStatus.PENDING_SHIP;
		this.updatedAt = OffsetDateTime.now();
		this.version++;

		// 发布状态变更事件
		publishStatusChangeEvent(oldStatus, oldSubStatus, "SYSTEM", "fulfillment-service", "开始履约");

		log.info("订单开始履约: orderNo={}", orderNo);
	}

	/**
	 * 货物发运
	 *
	 * @param shippingCompany 物流公司
	 * @param trackingNumber  运单号
	 */
	public void shipGoods(String shippingCompany, String trackingNumber) {
		if (this.mainStatus != OrderMainStatus.FULFILLING) {
			throw new IllegalStateException(
				String.format("订单状态不允许发货: orderNo=%s, currentStatus=%s",
					orderNo, mainStatus));
		}

		OrderSubStatus oldSubStatus = this.subStatus;

		this.subStatus = OrderSubStatus.SHIPPED;
		this.shippedAt = OffsetDateTime.now();
		this.updatedAt = OffsetDateTime.now();
		this.version++;

		// 发布发货事件 (这里可以创建专门的发货事件)
		publishStatusChangeEvent(
			this.mainStatus, oldSubStatus,
			"MERCHANT", this.merchantId,
			String.format("货物发运: %s-%s", shippingCompany, trackingNumber)
		);

		log.info("订单发货: orderNo={}, shippingCompany={}, trackingNumber={}",
			orderNo, shippingCompany, trackingNumber);
	}

	/**
	 * 确认收货
	 *
	 * @param confirmedBy 确认者 (USER/SYSTEM)
	 * @param confirmerId 确认者ID
	 */
	public void confirmReceipt(String confirmedBy, String confirmerId) {
		validateStatusTransition(OrderMainStatus.FULFILLING, "确认收货");

		OrderMainStatus oldStatus = this.mainStatus;
		OrderSubStatus oldSubStatus = this.subStatus;

		this.mainStatus = OrderMainStatus.COMPLETED;
		this.subStatus = OrderSubStatus.COMPLETED;
		this.completedAt = OffsetDateTime.now();
		this.updatedAt = OffsetDateTime.now();
		this.version++;

		publishStatusChangeEvent(oldStatus, oldSubStatus, confirmedBy, confirmerId, "确认收货");

		log.info("订单完成: orderNo={}, confirmedBy={}:{}", orderNo, confirmedBy, confirmerId);
	}

	/**
	 * 取消订单
	 *
	 * @param cancelledBy 取消者类型
	 * @param cancellerId 取消者ID
	 * @param reason      取消原因
	 */
	public void cancelOrder(String cancelledBy, String cancellerId, String reason) {
		// 只有特定状态下才能取消
		if (!canCancel()) {
			throw new IllegalStateException(
				String.format("订单状态不允许取消: orderNo=%s, currentStatus=%s",
					orderNo, mainStatus));
		}

		OrderMainStatus oldStatus = this.mainStatus;
		OrderSubStatus oldSubStatus = this.subStatus;

		this.mainStatus = OrderMainStatus.CANCELLED;
		this.subStatus = OrderSubStatus.CANCELLED;
		this.updatedAt = OffsetDateTime.now();
		this.version++;

		publishStatusChangeEvent(oldStatus, oldSubStatus, cancelledBy, cancellerId, reason);

		log.info("订单取消: orderNo={}, cancelledBy={}:{}, reason={}",
			orderNo, cancelledBy, cancellerId, reason);
	}

	// ============= 辅助方法 =============

	/**
	 * 验证状态转换
	 */
	private void validateStatusTransition(OrderMainStatus expectedStatus, String operation) {
		if (this.mainStatus != expectedStatus) {
			throw new IllegalStateException(
				String.format("订单状态不允许%s: orderNo=%s, currentStatus=%s, expectedStatus=%s",
					operation, orderNo, mainStatus, expectedStatus));
		}
	}

	/**
	 * 发布状态变更事件
	 */
	private void publishStatusChangeEvent(OrderMainStatus oldMainStatus,
	                                      OrderSubStatus oldSubStatus,
	                                      String actorType,
	                                      String actorId,
	                                      String reason) {
		OrderStatusChangedEvent statusEvent = new OrderStatusChangedEvent(
			UUID.randomUUID(),
			this.orderNo,
			OffsetDateTime.now(),
			MDC.get("traceId"),
			new OrderStatusChangedEvent.StatusChangeDetails(
				oldMainStatus.name(),
				this.mainStatus.name(),
				oldSubStatus.name(),
				this.subStatus.name(),
				actorType,
				actorId,
				reason
			)
		);
		addDomainEvent(statusEvent);
	}

	/**
	 * 检查是否可以取消
	 */
	private boolean canCancel() {
		return this.mainStatus == OrderMainStatus.PENDING_PAYMENT
			|| this.mainStatus == OrderMainStatus.PAID
			|| this.mainStatus == OrderMainStatus.FULFILLING;
	}

	/**
	 * 添加领域事件
	 */
	private void addDomainEvent(DomainEvent event) {
		this.domainEvents.add(event);
		log.debug("添加领域事件: orderNo={}, eventType={}, eventId={}",
			orderNo, event.getType(), event.getEventId());
	}

	/**
	 * 获取并清空领域事件
	 *
	 * @return 未发布的事件列表
	 */
	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(this.domainEvents);
		this.domainEvents.clear();
		return events;
	}

	/**
	 * 检查是否有未发布的事件
	 */
	public boolean hasPendingEvents() {
		return !this.domainEvents.isEmpty();
	}

	/**
	 * 更新状态 (由状态机调用)
	 */
	public void updateStatus(OrderMainStatus newMainStatus, OrderSubStatus newSubStatus) {
		if (this.mainStatus != newMainStatus || this.subStatus != newSubStatus) {
			log.info("更新订单状态: orderNo={}, {}:{} -> {}:{}",
				orderNo, this.mainStatus, this.subStatus, newMainStatus, newSubStatus);

			this.mainStatus = newMainStatus;
			this.subStatus = newSubStatus;
			this.updatedAt = OffsetDateTime.now();
			this.version++;
		}
	}

	/**
	 * 获取订单摘要信息
	 */
	public String getOrderSummary() {
		return String.format("Order[%s]: %s:%s, userId=%s, amount=%s",
			orderNo, mainStatus, subStatus, userId, totalAmount);
	}

	/**
	 * 检查订单是否处于终态
	 */
	public boolean isInFinalState() {
		return this.mainStatus == OrderMainStatus.COMPLETED
			|| this.mainStatus == OrderMainStatus.CANCELLED;
	}

	/**
	 * 检查订单是否已支付
	 */
	public boolean isPaid() {
		return this.mainStatus != OrderMainStatus.PENDING_PAYMENT;
	}
}