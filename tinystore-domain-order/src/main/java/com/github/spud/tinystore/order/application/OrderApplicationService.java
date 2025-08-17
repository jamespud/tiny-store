package com.github.spud.tinystore.order.application;

import com.github.spud.tinystore.infrastrucutre.domain.order.Order;
import com.github.spud.tinystore.infrastrucutre.domain.order.OrderOutbox;
import com.github.spud.tinystore.infrastrucutre.domain.order.OrderOutbox.Type;
import com.github.spud.tinystore.infrastrucutre.domain.payment.PaymentIntent;
import com.github.spud.tinystore.infrastrucutre.service.RedisIdempotencyStore;
import com.github.spud.tinystore.infrastrucutre.service.UserIdProvider;
import com.github.spud.tinystore.order.api.dto.Settlement;
import com.github.spud.tinystore.order.api.dto.SettlementRequest;
import com.github.spud.tinystore.order.api.error.OrderBusinessException;
import com.github.spud.tinystore.order.api.error.OrderErrorCode;
import com.github.spud.tinystore.order.domain.client.PaymentDomainService;
import com.github.spud.tinystore.order.domain.repository.OrderOutBoxRepository;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import com.github.spud.tinystore.order.domain.service.OrderRedisOperator;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO.Snapshot;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO.Summary;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
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
	private KafkaTemplate<String, Object> kafkaTemplate;
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
}
