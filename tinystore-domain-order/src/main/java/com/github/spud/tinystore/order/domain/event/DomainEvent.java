package com.github.spud.tinystore.order.domain.event;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 领域事件基础接口
 * 所有订单相关的领域事件都应该实现此接口
 *
 * @author Spud
 * @date 2025/9/29
 */
public interface DomainEvent {

	/**
	 * 事件唯一标识
	 *
	 * @return 事件ID
	 */
	UUID getEventId();

	/**
	 * 订单号
	 *
	 * @return 订单号
	 */
	String getOrderNo();

	/**
	 * 事件发生时间
	 *
	 * @return 发生时间
	 */
	OffsetDateTime getOccurredAt();

	/**
	 * 事件类型
	 *
	 * @return 事件类型标识
	 */
	String getType();

	/**
	 * 事件载荷数据
	 *
	 * @return 载荷数据
	 */
	Map<String, Object> getPayload();

	/**
	 * 链路追踪ID
	 *
	 * @return traceId
	 */
	String getTraceId();
}