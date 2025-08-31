package com.github.spud.tinystore.order.application;

import com.github.spud.tinystore.infrastrucutre.domain.order.Order;
import com.github.spud.tinystore.infrastrucutre.domain.order.OrderOutbox;
import com.github.spud.tinystore.infrastrucutre.domain.order.OrderOutbox.Type;
import com.github.spud.tinystore.infrastrucutre.domain.payment.PaymentIntent;
import com.github.spud.tinystore.infrastrucutre.service.RedisIdempotencyStore;
import com.github.spud.tinystore.infrastrucutre.service.UserIdProvider;
import com.github.spud.tinystore.order.api.dto.CancelRequest;
import com.github.spud.tinystore.order.api.dto.Settlement;
import com.github.spud.tinystore.order.api.dto.SettlementRequest;
import com.github.spud.tinystore.order.api.error.OrderBusinessException;
import com.github.spud.tinystore.order.api.error.OrderErrorCode;
import com.github.spud.tinystore.order.constant.OrderStatus;
import com.github.spud.tinystore.order.domain.client.PaymentDomainService;
import com.github.spud.tinystore.order.domain.enums.CancelDecisionType;
import com.github.spud.tinystore.order.domain.repository.OrderOutBoxRepository;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import com.github.spud.tinystore.order.domain.service.OrderApproveService;
import com.github.spud.tinystore.order.domain.service.OrderCancelDomainService;
import com.github.spud.tinystore.order.domain.service.OrderRedisOperator;
import com.github.spud.tinystore.order.domain.vo.CancelOrderVo;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO.Snapshot;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO.Summary;
import com.github.spud.tinystore.order.infrastructure.compensation.ResourceReleaseTask;
import com.github.spud.tinystore.order.infrastructure.compensation.ResourceReleaseTaskRepository;
import com.github.spud.tinystore.order.infrastructure.service.ResourceReleaseService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/8/12
 */
@Slf4j
@Transactional
@Service
public class OrderApplicationService {

	@Autowired
	private OrderOutBoxRepository orderOutBoxRepository;

	@Value("${tinystore.idempotency.ttl-seconds:600}")
	private long idempotencyTtlSeconds;

	@Autowired
	private OrderRedisOperator redisOperator;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private PaymentDomainService paymentDomainService;

	@Autowired
	private UserIdProvider userIdProvider;

	@Autowired
	private PricingService pricingService;

	@Autowired
	private RedisIdempotencyStore idempotencyService;

	@Autowired
	private OrderCancelDomainService orderCancelDomainService;
	@Autowired
	private OrderApproveService orderApproveService;
	@Autowired
	private ResourceReleaseService resourceReleaseService;
	@Autowired(required = false)
	private ResourceReleaseTaskRepository resourceReleaseTaskRepository;

	public SettlementPreviewVO preCheckSettlement(SettlementRequest request) {
		Settlement settlement = request.toSettlement();
		String userId = userIdProvider.getCurrentUserId();
		// 补全商品信息并计算价格
		Summary summary = pricingService.calculateSummary(settlement);
		List<Snapshot> snapshots = pricingService.generateSnapshots(settlement);

		// 生成令牌
		String idempotencyKey = idempotencyService.generateIdempotencyKey("order", "previewSettlement");
		// 存储预览数据
		boolean acquire = idempotencyService.tryAcquire(idempotencyKey, userId);
		if (!acquire) {
			log.warn("Failed to acquire idempotency key: {} for user: {}", idempotencyKey, userId);
			return null;
		}
		LocalDateTime expireAt = LocalDateTime.from(Instant.now().plusSeconds(idempotencyTtlSeconds));
		return new SettlementPreviewVO(snapshots, summary, idempotencyKey, expireAt);
	}

	public PaymentIntent executeBySettlement(Settlement bill) {
		String userId = userIdProvider.getCurrentUserId();
		boolean unused = idempotencyService.verifyIdempotencyKey(bill.getIdempotencyKey(), userId);
		if (!unused) {
			log.info("Idempotency key {} is already used by user {}", bill.getIdempotencyKey(), userId);
			return null;
		}
		List<Snapshot> snapshots = pricingService.generateSnapshots(bill);
		Summary summary = pricingService.calculateSummary(bill);
		// 预扣库存
		boolean preFlag = redisOperator.reduceStock(bill);
		if (!preFlag) {
			log.debug("预扣库存失败");
			throw new OrderBusinessException(OrderErrorCode.STOCK_INSUFFICIENT, "库存不足");
		}
		List<Order> orders = snapshots.stream().map(s -> s.toOrderLine(userId))
			.toList();
		orderRepository.saveAll(orders);
		// 保存 OrderOutbox 事件
		snapshots.stream()
			.map(o -> createOrderOutboxEvent(o.toOrderLine(userId), Type.OrderCreated, o))
			.forEach(orderOutBoxRepository::save);
		// TODO: 发布领域事件
		PaymentIntent intent = paymentDomainService.createPaymentIntent(summary.total(), "", "");
		return intent;
	}

	/**
	 * 取消订单预览
	 *
	 * @return 返回幂等key
	 */
	public String preCheckCancel() {
		String userId = userIdProvider.getCurrentUserId();
		String key = idempotencyService.generateIdempotencyKey("order", "cancel");
		boolean acquired = idempotencyService.tryAcquire(key, userId);
		if (!acquired) {
			log.warn("取消预检幂等键获取失败 userId={} key={}", userId, key);
			throw new OrderBusinessException(OrderErrorCode.IDEMPOTENT_REPLAY, "取消预检幂等键获取失败");
		}
		return key;
	}

	/**
	 * 取消订单
	 *
	 * @param request 取消订单请求
	 * @return 取消订单结果
	 */
	@Transactional
	public CancelOrderVo cancelOrder(CancelRequest request) {
		String userId = userIdProvider.getCurrentUserId();
		String idemKey = request.getIdempotencyKey();
		if (idemKey == null || idemKey.isBlank()) {
			throw new OrderBusinessException(OrderErrorCode.IDEMPOTENT_REPLAY, "缺少幂等键");
		}
		// 幂等验证（若已使用返回缓存 TODO: 当前 RedisIdempotencyStore 仅校验，不存结果）
		boolean fresh = idempotencyService.verifyIdempotencyKey(idemKey, userId);
		if (!fresh) {
			// 已处理过：简单返回占位（TODO: 未来可从缓存读取完整结果）
			return CancelOrderVo.builder()
				.orderId(request.getOrderId().toString())
				.decisionType(CancelDecisionType.ALLOW_SIMPLE)
				.finalStatus(OrderStatus.CANCELLED)
				.nextActionHint("幂等：订单取消已处理")
				.build();
		}
		UUID userUuid = UUID.fromString(userId);
		Order order = orderRepository.findByIdAndUserId(request.getOrderId(), userUuid)
			.orElseThrow(() -> new OrderBusinessException(OrderErrorCode.ORDER_NOT_FOUND, "订单不存在"));
		String currentStatus = order.getOrderStatus();
		if (OrderStatus.CANCELLED.getCode().equals(currentStatus)) {
			return CancelOrderVo.builder()
				.orderId(order.getId())
				.decisionType(CancelDecisionType.ALLOW_SIMPLE)
				.finalStatus(OrderStatus.CANCELLED)
				.nextActionHint("订单已取消")
				.build();
		}
		CancelDecisionType decision = orderCancelDomainService.decide(order, Instant.now());
		switch (decision) {
			case ALLOW_SIMPLE -> {
				int updated = orderRepository.cancelWithVersion(request.getOrderId(), order.getVersion(),
					currentStatus,
					OrderStatus.CANCELLED.getCode(), request.getReasonCode(), OffsetDateTime.now(),
					OffsetDateTime.now());
				if (updated == 0) {
					throw new OrderBusinessException(OrderErrorCode.ALREADY_PROCESSING,
						"订单取消冲突,请重试");
				}
				try {
					resourceReleaseService.release(order);
				} catch (Exception ex) {
					log.warn("资源释放失败, orderId={}", order.getId(), ex);
					createCompensationTask(order, request.getReasonCode(), ex.getMessage());
				}
				orderOutBoxRepository.save(createOrderOutboxEvent(order, Type.OrderCancelled, Map.of(
					"decision", decision.name(),
					"reason", request.getReasonCode(),
					"oldStatus", currentStatus,
					"newStatus", OrderStatus.CANCELLED.getCode(),
					"idempotencyKey", idemKey
				)));
				return buildCancelResponse(order, decision, OrderStatus.CANCELLED);
			}
			case ALLOW_WITH_MERCHANT_APPROVAL -> {
				orderApproveService.sendApproveMessage(order.getId());
				orderOutBoxRepository.save(createOrderOutboxEvent(order, Type.OrderCancelled, Map.of(
					"decision", decision.name(),
					"reason", request.getReasonCode(),
					"oldStatus", currentStatus,
					"newStatus", currentStatus,
					"idempotencyKey", idemKey,
					"note", "等待商家审批"
				)));
				return buildCancelResponse(order, decision, OrderStatus.valueOf(currentStatus));
			}
			default -> throw new OrderBusinessException(OrderErrorCode.ORDER_STATE_NOT_CANCELABLE,
				"当前订单状态不允许取消");
		}
	}

	/**
	 * 构建取消响应
	 */
	private CancelOrderVo buildCancelResponse(Order order, CancelDecisionType decisionType,
		OrderStatus finalStatus) {
		return CancelOrderVo.builder()
			.orderId(order.getId())
			.decisionType(decisionType)
			.finalStatus(finalStatus)
			.nextActionHint(getNextActionHint(decisionType))
			.build();
	}

	/**
	 * 获取下一步操作提示
	 */
	private String getNextActionHint(CancelDecisionType decisionType) {
		return switch (decisionType) {
			case ALLOW_SIMPLE -> "订单已成功取消";
			case ALLOW_WITH_MERCHANT_APPROVAL -> "等待商家审批";
			default -> "";
		};
	}

	public OrderOutbox createOrderOutboxEvent(Order order, OrderOutbox.Type eventType,
		Object payload) {
		OrderOutbox outbox = new OrderOutbox();
		outbox.setAggregateId(order.getId());
		outbox.setType(eventType);
		outbox.setPayloadJson(Map.of(
			"orderId", order.getId(),
			"payload", payload
		));
		return outbox;
	}

	private void createCompensationTask(Order order, String reasonCode, String errorMsg) {
		if (resourceReleaseTaskRepository == null) {
			log.debug("ResourceReleaseTaskRepository 未注入, 跳过补偿任务创建 orderId={}", order.getId());
			return;
		}
		try {
			ResourceReleaseTask task = new ResourceReleaseTask();
			try {
				java.util.UUID orderUuid = java.util.UUID.fromString(order.getId());
				task.setOrderId(orderUuid);
			} catch (IllegalArgumentException e) {
				log.warn("订单ID不是UUID格式，无法写入补偿任务 orderId={}", order.getId());
				return;
			}
			task.setOrderStatusAtFail(order.getOrderStatus());
			task.setResourceType("ALL");
			task.setRetryCount(0);
			task.setNextRetryTime(OffsetDateTime.now().plusMinutes(1));
			task.setLastError(
				errorMsg != null ? errorMsg.substring(0, Math.min(400, errorMsg.length())) : null);
			task.setCompleted(false);
			resourceReleaseTaskRepository.save(task);
			log.info("创建资源释放补偿任务成功 taskId={} orderId={} reason={}", task.getId(),
				order.getId(), reasonCode);
		} catch (Exception e) {
			log.error("创建资源释放补偿任务失败 orderId={}", order.getId(), e);
		}
	}
}
