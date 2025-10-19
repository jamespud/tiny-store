package com.github.spud.tinystore.order.infrastructure.statemachine;

import com.github.spud.tinystore.order.infrastructure.statemachine.enums.OrderEvent;
import com.github.spud.tinystore.order.infrastructure.statemachine.enums.OrderMainStatus;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.atn.Transition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

/**
 * 订单状态机配置 基于 Spring State Machine 实现订单状态流转控制
 */
@Slf4j
@Configuration
@EnableStateMachine
public class OrderStateMachineConfig extends
	StateMachineConfigurerAdapter<OrderMainStatus, OrderEvent> {

	@Override
	public void configure(StateMachineStateConfigurer<OrderMainStatus, OrderEvent> states)
		throws Exception {
		states
			.withStates()
			// 初始状态
			.initial(OrderMainStatus.PENDING_PAYMENT)

			// 所有状态
			.states(java.util.EnumSet.allOf(OrderMainStatus.class))

			// 终态
			.end(OrderMainStatus.COMPLETED)
			.end(OrderMainStatus.CANCELLED);
	}

	@Override
	public void configure(StateMachineTransitionConfigurer<OrderMainStatus, OrderEvent> transitions)
		throws Exception {
		transitions
			// 支付成功：待支付 -> 已支付
			.withExternal()
			.source(OrderMainStatus.PENDING_PAYMENT)
			.target(OrderMainStatus.PAID)
			.event(OrderEvent.PAYMENT_SUCCEEDED)
			.action(context -> {
				log.info("Order payment succeeded: orderNo={}",
					context.getExtendedState().get("orderNo", Object.class));
			})

			// 支付失败：待支付 -> 已取消
			.and()
			.withExternal()
			.source(OrderMainStatus.PENDING_PAYMENT)
			.target(OrderMainStatus.CANCELLED)
			.event(OrderEvent.PAYMENT_FAILED)
			.action(context -> {
				log.info("Order payment failed: orderNo={}",
					context.getExtendedState().get("orderNo"));
			})

			// 开始履约：已支付 -> 履约中
			.and()
			.withExternal()
			.source(OrderMainStatus.PAID)
			.target(OrderMainStatus.FULFILLING)
			.event(OrderEvent.FULFILLMENT_STARTED)
			.action(context -> {
				log.info("Order fulfillment started: orderNo={}",
					context.getExtendedState().get("orderNo"));
			})

			// 发货：履约中 -> 履约中 (子状态变更)
			.and()
			.withInternal()
			.source(OrderMainStatus.FULFILLING)
			.event(OrderEvent.GOODS_SHIPPED)
			.action(context -> {
				log.info("Goods shipped: orderNo={}",
					context.getExtendedState().get("orderNo"));
			})

			// 确认收货：履约中 -> 已完成
			.and()
			.withExternal()
			.source(OrderMainStatus.FULFILLING)
			.target(OrderMainStatus.COMPLETED)
			.event(OrderEvent.GOODS_RECEIVED)
			.action(context -> {
				log.info("Goods received, order completed: orderNo={}",
					context.getExtendedState().get("orderNo"));
			})

			// 超时自动确认：履约中 -> 已完成
			.and()
			.withExternal()
			.source(OrderMainStatus.FULFILLING)
			.target(OrderMainStatus.COMPLETED)
			.event(OrderEvent.AUTO_CONFIRM_TIMEOUT)
			.action(context -> {
				log.info("Order auto-confirmed due to timeout: orderNo={}",
					context.getExtendedState().get("orderNo"));
			})

			// 用户取消：待支付 -> 已取消
			.and()
			.withExternal()
			.source(OrderMainStatus.PENDING_PAYMENT)
			.target(OrderMainStatus.CANCELLED)
			.event(OrderEvent.USER_CANCELLED)
			.action(context -> {
				log.info("Order cancelled by user: orderNo={}",
					context.getExtendedState().get("orderNo"));
			})

			// 商户取消：已支付/履约中 -> 已取消
			.and()
			.withExternal()
			.source(OrderMainStatus.PAID)
			.target(OrderMainStatus.CANCELLED)
			.event(OrderEvent.MERCHANT_CANCELLED)
			.action(context -> {
				log.info("Order cancelled by merchant: orderNo={}",
					context.getExtendedState().get("orderNo"));
			})

			.and()
			.withExternal()
			.source(OrderMainStatus.FULFILLING)
			.target(OrderMainStatus.CANCELLED)
			.event(OrderEvent.MERCHANT_CANCELLED)
			.action(context -> {
				log.info("Order cancelled by merchant during fulfillment: orderNo={}",
					context.getExtendedState().get("orderNo"));
			})

			// 系统取消：任意状态 -> 已取消
			.and()
			.withExternal()
			.source(OrderMainStatus.PENDING_PAYMENT)
			.target(OrderMainStatus.CANCELLED)
			.event(OrderEvent.SYSTEM_CANCELLED)

			.and()
			.withExternal()
			.source(OrderMainStatus.PAID)
			.target(OrderMainStatus.CANCELLED)
			.event(OrderEvent.SYSTEM_CANCELLED)

			.and()
			.withExternal()
			.source(OrderMainStatus.FULFILLING)
			.target(OrderMainStatus.CANCELLED)
			.event(OrderEvent.SYSTEM_CANCELLED);
	}

	/**
	 * 状态机监听器
	 */
	@Bean
	public StateMachineListener<OrderMainStatus, OrderEvent> stateMachineListener() {
		return new StateMachineListenerAdapter<OrderMainStatus, OrderEvent>() {

			@Override
			public void stateChanged(State<OrderMainStatus, OrderEvent> from,
				State<OrderMainStatus, OrderEvent> to) {
				if (from != null && to != null) {
					log.info("State machine transition: {} -> {}",
						from.getId(), to.getId());
				}
			}

			@Override
			public void eventNotAccepted(org.springframework.messaging.Message<OrderEvent> event) {
				log.warn("State machine event not accepted: {}", event.getPayload());
			}

			@Override
			public void transitionStarted(Transition<OrderMainStatus, OrderEvent> transition) {
				log.debug("State machine transition started: {} -> {} on event {}",
					transition.getSource().getId(),
					transition.getTarget().getId(),
					transition.getTrigger().getEvent());
			}

			@Override
			public void transitionEnded(Transition<OrderMainStatus, OrderEvent> transition) {
				log.debug("State machine transition ended: {} -> {} on event {}",
					transition.getSource().getId(),
					transition.getTarget().getId(),
					transition.getTrigger().getEvent());
			}
		};
	}
}