package com.github.spud.tinystore.order.domain.statemachine;

import com.github.spud.tinystore.order.domain.model.OrderAggregate;
import com.github.spud.tinystore.order.infrastructure.statemachine.enums.OrderEvent;

import java.util.Map;
import lombok.Getter;

/**
 * 订单状态机服务接口
 * 负责管理订单状态的合法性转换
 *
 * @author Spud
 * @date 2025/9/29
 */
public interface OrderStateMachineService {

	/**
	 * 执行状态转换
	 *
	 * @param order   订单聚合
	 * @param event   触发事件
	 * @param context 上下文数据
	 * @return 转换后的订单聚合
	 * @throws IllegalStateTransitionException 非法状态转换异常
	 */
	OrderAggregate transition(OrderAggregate order, OrderEvent event, Map<String, Object> context);

	/**
	 * 检查状态转换是否合法
	 *
	 * @param order 当前订单聚合
	 * @param event 要触发的事件
	 * @return 是否可以执行转换
	 */
	boolean canTransition(OrderAggregate order, OrderEvent event);

	/**
	 * 非法状态转换异常
	 */
	@Getter
	class IllegalStateTransitionException extends RuntimeException {
		private final String currentMainStatus;
		private final String currentSubStatus;
		private final String event;

		public IllegalStateTransitionException(String currentMainStatus, String currentSubStatus, String event) {
			super(String.format("Illegal state transition from %s:%s with event %s",
				currentMainStatus, currentSubStatus, event));
			this.currentMainStatus = currentMainStatus;
			this.currentSubStatus = currentSubStatus;
			this.event = event;
		}
	}
}
