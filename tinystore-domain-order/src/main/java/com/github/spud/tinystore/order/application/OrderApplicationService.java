package com.github.spud.tinystore.order.application;

import com.github.spud.tinystore.infrastrucutre.common.constant.MessageTopicConfig;
import com.github.spud.tinystore.infrastrucutre.domain.order.Order;
import com.github.spud.tinystore.infrastrucutre.domain.payment.PaymentIntent;
import com.github.spud.tinystore.infrastrucutre.service.RedisIdempotencyStore;
import com.github.spud.tinystore.infrastrucutre.service.UserIdProvider;
import com.github.spud.tinystore.order.api.dto.Settlement;
import com.github.spud.tinystore.order.api.dto.SettlementRequest;
import com.github.spud.tinystore.order.api.error.OrderBusinessException;
import com.github.spud.tinystore.order.api.error.OrderErrorCode;
import com.github.spud.tinystore.order.domain.client.PaymentDomainService;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import com.github.spud.tinystore.order.domain.service.OrderRedisOperator;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO.Snapshot;
import com.github.spud.tinystore.order.domain.vo.SettlementPreviewVO.Summary;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/8/12
 */
@Slf4j
@Transactional
@Service
public class OrderApplicationService {

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
		// 集成消息队列冻结部分库存
		try {
			CompletableFuture<SendResult<String, Object>> send = kafkaTemplate.send(
				MessageTopicConfig.FROZEN_STOCK_TOPIC, bill.getItems());
			SendResult<String, Object> result = send.get();
			if (result == null || result.getRecordMetadata() == null) {
				while (!redisOperator.revertStock(bill)) {
					log.debug("预扣库存失败，尝试恢复库存");
				}
				throw new RuntimeException("消息发送失败：未获取到元数据");
			}
			// 发送成功，继续后续逻辑
		} catch (Exception e) {
			// 发送失败，记录日志或抛出业务异常
			throw new RuntimeException("消息发送异常", e);
		}
		// TODO: 发布领域事件
		PaymentIntent intent = paymentDomainService.createPaymentIntent(summary.total(), "", "");
		return intent;
	}
}
