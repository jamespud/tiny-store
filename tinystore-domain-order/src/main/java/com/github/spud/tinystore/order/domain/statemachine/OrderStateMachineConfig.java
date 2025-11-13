package com.github.spud.tinystore.order.domain.statemachine;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;

/**
 * 订单状态机配置 基于 Spring State Machine 实现订单状态流转控制
 */
@Slf4j
@Configuration
@EnableStateMachineFactory
public class OrderStateMachineConfig extends
	StateMachineConfigurerAdapter<CoreFlowStatus, OrderEvent> {

	@Override
	public void configure(StateMachineStateConfigurer<CoreFlowStatus, OrderEvent> states)
		throws Exception {
		states
			.withStates()
			.initial(CoreFlowStatus.PENDING_PAYMENT)
			.states(java.util.EnumSet.allOf(CoreFlowStatus.class))
			.end(CoreFlowStatus.COMPLETED)
			.end(CoreFlowStatus.CANCELLED);
	}

	@Override
	public void configure(StateMachineTransitionConfigurer<CoreFlowStatus, OrderEvent> transitions)
		throws Exception {
		transitions
			// PENDING_PAYMENT -> PAID
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.PAID)
			.event(OrderEvent.PAYMENT_SUCCEEDED)
			.and()
			// PENDING_PAYMENT cancellation paths
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.PAYMENT_FAILED)
			.and()
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.PAYMENT_TIMEOUT)
			.and()
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.USER_CANCELLED)
			.and()
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.SYSTEM_CANCELLED)
			.and()
			// PAID -> ACCEPTED
			.withExternal()
			.source(CoreFlowStatus.PAID)
			.target(CoreFlowStatus.ACCEPTED)
			.event(OrderEvent.MERCHANT_ACCEPTED)
			.and()
			// PAID cancellation (merchant)
			.withExternal()
			.source(CoreFlowStatus.PAID)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.MERCHANT_CANCELLED)
			.and()
			// ACCEPTED -> FULFILLING
			.withExternal()
			.source(CoreFlowStatus.ACCEPTED)
			.target(CoreFlowStatus.FULFILLING)
			.event(OrderEvent.FULFILLMENT_STARTED)
			.and()
			// FULFILLING internal progress events
			.withInternal()
			.source(CoreFlowStatus.FULFILLING)
			.event(OrderEvent.GOODS_SHIPPED)
			.and()
			.withInternal()
			.source(CoreFlowStatus.FULFILLING)
			.event(OrderEvent.GOODS_DELIVERED)
			.and()
			// FULFILLING -> COMPLETED
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.COMPLETED)
			.event(OrderEvent.GOODS_RECEIVED)
			.and()
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.COMPLETED)
			.event(OrderEvent.AUTO_RECEIVE_TIMEOUT)
			.and()
			// FULFILLING -> CANCELLED (reject / merchant / system)
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.GOODS_REJECTED)
			.and()
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.MERCHANT_CANCELLED)
			.and()
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.SYSTEM_CANCELLED);
	}

	@Bean
	public StateMachineListener<CoreFlowStatus, OrderEvent> stateMachineListener() {
		return new StateMachineListenerAdapter<>() {
			@Override
			public void stateChanged(State<CoreFlowStatus, OrderEvent> from,
				State<CoreFlowStatus, OrderEvent> to) {
				if (from != null && to != null) {
					log.info("State machine transition: {} -> {}", from.getId(), to.getId());
				}
			}

			@Override
			public void eventNotAccepted(org.springframework.messaging.Message<OrderEvent> event) {
				log.warn("State machine event not accepted: {}", event.getPayload());
			}

			@Override
			public void transitionStarted(Transition<CoreFlowStatus, OrderEvent> transition) {
				CoreFlowStatus sourceId = transition.getSource() != null ? transition.getSource().getId() : null;
				CoreFlowStatus targetId = transition.getTarget() != null ? transition.getTarget().getId() : null;
				OrderEvent evt = transition.getTrigger() != null ? transition.getTrigger().getEvent() : null;
				log.debug("Transition started: {} -> {} on {}", sourceId, targetId, evt);
			}

			@Override
			public void transitionEnded(Transition<CoreFlowStatus, OrderEvent> transition) {
				CoreFlowStatus sourceId = transition.getSource() != null ? transition.getSource().getId() : null;
				CoreFlowStatus targetId = transition.getTarget() != null ? transition.getTarget().getId() : null;
				OrderEvent evt = transition.getTrigger() != null ? transition.getTrigger().getEvent() : null;
				log.debug("Transition ended: {} -> {} on {}", sourceId, targetId, evt);
			}
		};
	}
}