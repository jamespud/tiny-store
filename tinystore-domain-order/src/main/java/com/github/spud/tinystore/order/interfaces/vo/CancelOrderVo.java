package com.github.spud.tinystore.order.interfaces.vo;

import com.github.spud.tinystore.order.domain.enums.CancelDecisionType;
import com.github.spud.tinystore.order.domain.event.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * @author Spud
 * @date 2025/8/29
 */
@Data
@Builder
public class CancelOrderVo {

	/**
	 * 订单ID
	 */
	private String orderId;

	/**
	 * 最终状态
	 */
	private OrderStatus finalStatus;

	/**
	 * 决策类型
	 */
	private CancelDecisionType decisionType;

	/**
	 * 下一步操作提示
	 */
	private String nextActionHint;

	/**
	 * 服务器时间
	 */
	private Instant serverTime;
}
