package com.github.spud.tinystore.order.infrastructure.statemachine;

import com.github.spud.tinystore.order.domain.model.OrderAggregate;
import com.github.spud.tinystore.order.domain.statemachine.OrderStateMachineService;
import com.github.spud.tinystore.order.infrastructure.statemachine.enums.OrderEvent;
import com.github.spud.tinystore.order.infrastructure.statemachine.enums.OrderMainStatus;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.Lifecycle;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.service.StateMachineService;
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;

/**
 * 订单状态机服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateMachineServiceImpl implements OrderStateMachineService {

	private final StateMachineService<OrderMainStatus, OrderEvent> stateMachineService;
	private StateMachinePersister<OrderMainStatus, OrderEvent, String> persister;

	@PostConstruct
	public void init() {
		// 初始化状态机持久化器 (这里使用内存存储，生产环境应该使用 Redis)
		this.persister = new DefaultStateMachinePersister<>(new InMemoryStateMachinePersist());
	}

	public boolean sendEvent(String orderNo, OrderEvent event, Map<String, Object> context) {
		try {
			// 获取或创建状态机实例
			StateMachine<OrderMainStatus, OrderEvent> stateMachine = getStateMachine(orderNo);

			// 设置上下文信息
			if (context != null) {
				context.forEach((key, value) ->
					stateMachine.getExtendedState().getVariables().put(key, value));
			}

			// 设置订单号到扩展状态
			stateMachine.getExtendedState().getVariables().put("orderNo", orderNo);
			stateMachine.getExtendedState().getVariables().put("traceId", MDC.get("traceId"));

			// 发送事件
			boolean result = stateMachine.sendEvent(
				MessageBuilder.withPayload(event)
					.setHeader("orderNo", orderNo)
					.setHeader("traceId", MDC.get("traceId"))
					.build()
			);

			// 持久化状态机状态
			persister.persist(stateMachine, orderNo);

			log.info("State machine event sent: orderNo={}, event={}, result={}, currentState={}",
				orderNo, event, result, stateMachine.getState().getId());

			return result;

		} catch (Exception e) {
			log.error("Failed to send state machine event: orderNo={}, event={}",
				orderNo, event, e);
			return false;
		}
	}

	public OrderMainStatus getCurrentStatus(String orderNo) {
		try {
			StateMachine<OrderMainStatus, OrderEvent> stateMachine = getStateMachine(orderNo);
			return stateMachine.getState().getId();
		} catch (Exception e) {
			log.error("Failed to get current status: orderNo={}", orderNo, e);
			return null;
		}
	}

	public boolean canTransition(String orderNo, OrderEvent event) {
		try {
			StateMachine<OrderMainStatus, OrderEvent> stateMachine = getStateMachine(orderNo);

			// 检查当前状态是否可以接受该事件
			return stateMachine.getTransitions().stream()
				.anyMatch(transition ->
					transition.getSource().getId().equals(stateMachine.getState().getId())
						&& transition.getTrigger() != null
						&& event.equals(transition.getTrigger().getEvent()));

		} catch (Exception e) {
			log.error("Failed to check transition: orderNo={}, event={}", orderNo, event, e);
			return false;
		}
	}

	public void resetStateMachine(String orderNo, OrderMainStatus initialStatus) {
		try {
			// 创建新的状态机并设置初始状态
			StateMachine<OrderMainStatus, OrderEvent> stateMachine =
				stateMachineService.acquireStateMachine(orderNo);

			// 重置到指定状态
			stateMachine.stop();
			stateMachine.getStateMachineAccessor()
				.doWithAllRegions(access ->
					access.resetStateMachine(new DefaultStateMachineContext<>(
						initialStatus, null, null, new DefaultExtendedState())));
			stateMachine.start();

			// 持久化状态
			persister.persist(stateMachine, orderNo);

			log.info("State machine reset: orderNo={}, initialStatus={}", orderNo, initialStatus);

		} catch (Exception e) {
			log.error("Failed to reset state machine: orderNo={}, initialStatus={}",
				orderNo, initialStatus, e);
		}
	}

	/**
	 * 获取状态机实例
	 */
	private StateMachine<OrderMainStatus, OrderEvent> getStateMachine(String orderNo)
		throws Exception {
		StateMachine<OrderMainStatus, OrderEvent> stateMachine =
			stateMachineService.acquireStateMachine(orderNo);

		// 尝试从持久化存储恢复状态
		persister.restore(stateMachine, orderNo);

		// 如果状态机未启动，则启动它
		if (!((Lifecycle) stateMachine).isRunning()) {
			stateMachine.start();
		}

		return stateMachine;
	}

	@Override
	public OrderAggregate transition(OrderAggregate order, OrderEvent event,
		Map<String, Object> context) {
		return null;
	}

	@Override
	public boolean canTransition(OrderAggregate order, OrderEvent event) {
		return false;
	}

	/**
	 * 内存状态机持久化实现 (生产环境应该使用 Redis)
	 */
	private static class InMemoryStateMachinePersist
		implements StateMachinePersist<OrderMainStatus, OrderEvent, String> {

		private final Map<String, StateMachineContext<OrderMainStatus, OrderEvent>> storage =
			new HashMap<>();

		@Override
		public void write(StateMachineContext<OrderMainStatus, OrderEvent> context, String contextObj) {
			storage.put(contextObj, context);
		}

		@Override
		public StateMachineContext<OrderMainStatus, OrderEvent> read(String contextObj) {
			return storage.get(contextObj);
		}
	}
}