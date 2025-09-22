package com.github.spud.tinystore.order.domain.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.model.Order;
import com.github.spud.tinystore.order.domain.model.Outbox;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import com.github.spud.tinystore.order.domain.repository.OutboxRepository;
import com.github.spud.tinystore.order.domain.status.CoreFlowStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/9/6
 */
@Slf4j
@Service
public class OrderDomainService {
	
	private final OrderRepository repository;
	private final OutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;
	
	public OrderDomainService(OrderRepository repository, OutboxRepository outboxRepository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.outboxRepository = outboxRepository;
		this.objectMapper = objectMapper;
	}

	public void saveOrderLine(Order order) {
		// TODO: 实现订单保存逻辑
	}
	
	/**
	 * 加载订单用于更新（带版本锁）
	 * 
	 * @param orderId 订单ID
	 * @return 订单聚合
	 */
	public Order loadForUpdate(UUID orderId) {
		// TODO: 实现带版本锁的订单加载
		// TODO: 从 repository 加载订单
		// TODO: 检查订单是否存在
		throw new UnsupportedOperationException("loadForUpdate not implemented yet");
	}
	
	/**
	 * 保存订单（带版本检查）
	 * 
	 * @param order 订单聚合
	 * @param expectedVersion 期望版本号
	 */
	public void save(Order order, long expectedVersion) {
		// TODO: 实现带版本检查的订单保存
		// TODO: 使用乐观锁防止并发冲突
		// TODO: 版本不匹配时抛出异常
		throw new UnsupportedOperationException("save with version check not implemented yet");
	}
	
	/**
	 * 记录状态变更日志
	 * 
	 * @param orderId 订单ID
	 * @param from 原状态
	 * @param to 目标状态
	 * @param reason 变更原因
	 * @param actor 操作者
	 * @param key 幂等键或事件ID
	 */
	public void appendStatusLog(UUID orderId, CoreFlowStatus from, CoreFlowStatus to, 
	                           String reason, String actor, String key) {
		// TODO: 实现状态变更日志记录
		// TODO: 记录到审计表或日志系统
		// TODO: 包含时间戳、操作者、变更原因等信息
		throw new UnsupportedOperationException("appendStatusLog not implemented yet");
	}
	
	/**
	 * 记录 Outbox 事件
	 * 
	 * @param order 订单聚合
	 * @param eventType 事件类型
	 * @param payload 事件载荷
	 */
	public void recordOutbox(Order order, String eventType, Object payload) {
		try {
			// 序列化载荷为 JSON
			String payloadJson = objectMapper.writeValueAsString(payload);
			
			// 创建 Outbox 实体
			Outbox outbox = Outbox.builder()
				.eventId(UUID.randomUUID())
				.eventType(eventType)
				.aggregateId(extractOrderId(order)) // 待实现：从 Order 中提取 ID
				.aggregateType("Order")
				.payload(payloadJson)
				.occurredAt(Instant.now())
				.status(Outbox.PublishStatus.PENDING)
				.retryCount(0)
				.createdAt(Instant.now())
				.build();
			
			// 保存到数据库（在同一事务中）
			outboxRepository.save(outbox);
			
			log.debug("Outbox event recorded: eventId={}, eventType={}, aggregateId={}", 
				outbox.getEventId(), eventType, outbox.getAggregateId());
				
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize payload for outbox event: eventType={}", eventType, e);
			throw new RuntimeException("Failed to record outbox event", e);
		}
	}
	
	/**
	 * 从 Order 聚合中提取订单 ID
	 * TODO: 需要在 Order 类中添加 ID 字段或者通过其他方式获取
	 */
	private UUID extractOrderId(Order order) {
		// 生成订单ID - 当前使用UUID作为临时实现
		return UUID.randomUUID();
	}
}
