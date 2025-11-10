package com.github.spud.tinystore.order.application.service;

import com.github.spud.tinystore.order.domain.event.OrderDomainEvent;
import com.github.spud.tinystore.order.domain.model.OrderAggregate;
import com.github.spud.tinystore.order.infrastructure.audit.OrderStatusAuditService;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.idempotency.OrderIdempotencyService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单支付回调应用服务 实现"支付成功回调→状态机→Outbox→Kafka事件→读侧同步"垂直切片
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentCallbackAppService {

	private final OrderIdempotencyService idempotencyService;
	private final OutboxEventService outboxEventService;
	private final OrderStatusAuditService auditService;

	// private final OrderRepository orderRepository; // 需要注入订单仓储

	/**
	 * 处理支付成功回调 这是整个垂直切片的入口点
	 *
	 * @param request 支付回调请求
	 * @return 处理结果
	 */
	@Transactional
	public PaymentCallbackResult handlePaymentSuccess(PaymentSuccessCallbackRequest request) {
		String traceId = MDC.get("traceId");
		log.info("处理支付成功回调: orderNo={}, paymentTransactionId={}, traceId={}",
			request.getOrderNo(), request.getPaymentTransactionId(), traceId);

		try {
			// Step 1: 幂等性检查
			Optional<OrderIdempotencyService.IdempotencyResult> idempotencyResult =
				idempotencyService.checkAndCreateIdempotency(
					request.getRequestId(),
					request.getOrderNo(),
					"PAYMENT_SUCCESS_CALLBACK"
				);

			if (idempotencyResult.isPresent()) {
				if (idempotencyResult.get().isProcessing()) {
					log.info("支付回调正在处理中: orderNo={}, requestId={}",
						request.getOrderNo(), request.getRequestId());
					return PaymentCallbackResult.processing(request.getOrderNo());
				} else {
					log.info("支付回调已处理过: orderNo={}, requestId={}",
						request.getOrderNo(), request.getRequestId());
					return PaymentCallbackResult.duplicate(
						idempotencyResult.get().getOrderNo(),
						idempotencyResult.get().getResponseData()
					);
				}
			}

			// Step 2: 加载订单聚合
			OrderAggregate orderAggregate = loadOrderAggregate(request.getOrderNo());
			if (orderAggregate == null) {
				String errorMsg = "订单不存在: " + request.getOrderNo();
				idempotencyService.failIdempotency(request.getRequestId());
				return PaymentCallbackResult.failure(request.getOrderNo(), errorMsg);
			}

			// Step 3: 业务处理 - 订单支付成功
			// TODO: 
			orderAggregate.onPaymentSuccess(null);

			// Step 4: 持久化订单聚合 (更新数据库)
			saveOrderAggregate(orderAggregate);

			// Step 5: 处理领域事件 - 保存到 Outbox
			List<OrderDomainEvent> domainEvents = orderAggregate.pullDomainEvents();
			if (!domainEvents.isEmpty()) {
				outboxEventService.saveEvents(domainEvents);
				log.info("保存领域事件到 Outbox: orderNo={}, eventCount={}",
					request.getOrderNo(), domainEvents.size());
			}

			// Step 6: 记录状态变更审计
			auditService.recordSystemStatusChange(
				request.getOrderNo(),
				"PENDING_PAYMENT",
				orderAggregate.getMainStatus().name(),
				"payment-callback-service",
				"支付成功回调处理",
				domainEvents.isEmpty() ? null : domainEvents.get(0).getEventId()
			);

			// Step 7: 完成幂等性处理
			PaymentCallbackResult result = PaymentCallbackResult.success(
				request.getOrderNo(),
				orderAggregate.getMainStatus().name(),
				orderAggregate.getSubStatus().name()
			);

			idempotencyService.completeIdempotency(request.getRequestId(), result);

			log.info("支付成功回调处理完成: orderNo={}, newStatus={}:{}",
				request.getOrderNo(),
				orderAggregate.getMainStatus(),
				orderAggregate.getSubStatus());

			return result;

		} catch (Exception e) {
			log.error("支付成功回调处理异常: orderNo={}", request.getOrderNo(), e);
			idempotencyService.failIdempotency(request.getRequestId());
			throw new RuntimeException("支付回调处理失败", e);
		}
	}

	/**
	 * 查询支付回调处理结果
	 *
	 * @param requestId 请求ID
	 * @return 处理结果
	 */
	@Transactional(readOnly = true)
	public Optional<PaymentCallbackResult> queryCallbackResult(String requestId) {
		// 这里可以从幂等性表查询结果
		// 实现省略...
		return Optional.empty();
	}

	/**
	 * 加载订单聚合 (需要从仓储层加载)
	 */
	private OrderAggregate loadOrderAggregate(String orderNo) {
		// 这里需要注入 OrderRepository 来加载聚合
		// 暂时返回一个模拟对象用于演示
		log.warn("模拟加载订单聚合: orderNo={}", orderNo);

		return null; // TODO: 实现实际加载逻辑
	}

	/**
	 * 保存订单聚合 (需要持久化到数据库)
	 */
	private void saveOrderAggregate(OrderAggregate orderAggregate) {
		// 这里需要注入 OrderRepository 来保存聚合
		log.info("保存订单聚合: {}", orderAggregate);
	}

	// ============= 内部类 =============

	/**
	 * 支付成功回调请求
	 */
	public static class PaymentSuccessCallbackRequest {

		private String requestId;           // 请求唯一标识（幂等键）
		private String orderNo;             // 订单号
		private String paymentTransactionId; // 支付流水号
		private String paymentMethod;       // 支付方式
		private BigDecimal amount;          // 支付金额
		private String signature;          // 签名
		private Map<String, Object> extraData; // 扩展数据

		// Getters and Setters
		public String getRequestId() {
			return requestId;
		}

		public void setRequestId(String requestId) {
			this.requestId = requestId;
		}

		public String getOrderNo() {
			return orderNo;
		}

		public void setOrderNo(String orderNo) {
			this.orderNo = orderNo;
		}

		public String getPaymentTransactionId() {
			return paymentTransactionId;
		}

		public void setPaymentTransactionId(String paymentTransactionId) {
			this.paymentTransactionId = paymentTransactionId;
		}

		public String getPaymentMethod() {
			return paymentMethod;
		}

		public void setPaymentMethod(String paymentMethod) {
			this.paymentMethod = paymentMethod;
		}

		public BigDecimal getAmount() {
			return amount;
		}

		public void setAmount(BigDecimal amount) {
			this.amount = amount;
		}

		public String getSignature() {
			return signature;
		}

		public void setSignature(String signature) {
			this.signature = signature;
		}

		public Map<String, Object> getExtraData() {
			return extraData;
		}

		public void setExtraData(Map<String, Object> extraData) {
			this.extraData = extraData;
		}
	}

	/**
	 * 支付回调处理结果
	 */
	public static class PaymentCallbackResult {

		private String status;      // SUCCESS, PROCESSING, DUPLICATE, FAILURE
		private String orderNo;
		private String message;
		private String mainStatus;
		private String subStatus;
		private Object data;

		private PaymentCallbackResult(String status, String orderNo, String message) {
			this.status = status;
			this.orderNo = orderNo;
			this.message = message;
		}

		public static PaymentCallbackResult success(String orderNo, String mainStatus,
			String subStatus) {
			PaymentCallbackResult result = new PaymentCallbackResult("SUCCESS", orderNo, "处理成功");
			result.mainStatus = mainStatus;
			result.subStatus = subStatus;
			return result;
		}

		public static PaymentCallbackResult processing(String orderNo) {
			return new PaymentCallbackResult("PROCESSING", orderNo, "处理中");
		}

		public static PaymentCallbackResult duplicate(String orderNo, String previousResult) {
			PaymentCallbackResult result = new PaymentCallbackResult("DUPLICATE", orderNo, "重复请求");
			result.data = previousResult;
			return result;
		}

		public static PaymentCallbackResult failure(String orderNo, String errorMessage) {
			return new PaymentCallbackResult("FAILURE", orderNo, errorMessage);
		}

		// Getters
		public String getStatus() {
			return status;
		}

		public String getOrderNo() {
			return orderNo;
		}

		public String getMessage() {
			return message;
		}

		public String getMainStatus() {
			return mainStatus;
		}

		public String getSubStatus() {
			return subStatus;
		}

		public Object getData() {
			return data;
		}
	}
}