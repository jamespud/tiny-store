package com.github.spud.tinystore.order.domain.event;

import com.github.spud.tinystore.domain.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 领域事件基础接口
 * 所有订单相关的领域事件都应该实现此接口
 *
 * @author Spud
 * @date 2025/9/29
 */
public interface OrderDomainEvent extends DomainEvent {
	
	/**
	 * 订单号
	 *
	 * @return 订单号
	 */
	String getOrderId();


}