package com.github.spud.tinystore.order.domain.statemachine;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import com.github.spud.tinystore.order.domain.statemachine.guard.TerminalStateGuard;
import com.github.spud.tinystore.order.domain.statemachine.guard.PaymentPhaseGuard;
import com.github.spud.tinystore.order.domain.statemachine.guard.FulfillmentMutexGuard;
import com.github.spud.tinystore.order.domain.statemachine.guard.CanPayGuard;
import com.github.spud.tinystore.order.domain.statemachine.guard.CanShipGuard;
import com.github.spud.tinystore.order.domain.statemachine.guard.CanConfirmDeliveryGuard;
import com.github.spud.tinystore.order.domain.statemachine.guard.CanReceiveGuard;
import com.github.spud.tinystore.order.domain.statemachine.guard.CanCancelGuard;
import com.github.spud.tinystore.order.domain.statemachine.action.ApplyPaymentSucceededAction;
import com.github.spud.tinystore.order.domain.statemachine.action.ApplyMerchantAcceptedAction;
import com.github.spud.tinystore.order.domain.statemachine.action.ApplyGoodsShippedAction;
import com.github.spud.tinystore.order.domain.statemachine.action.ApplyGoodsDeliveredAction;
import com.github.spud.tinystore.order.domain.statemachine.action.ApplyGoodsReceivedAction;
import com.github.spud.tinystore.order.domain.statemachine.action.ApplyUserCancelledAction;
import com.github.spud.tinystore.order.domain.statemachine.action.ApplyTimeoutCancelledAction;
import com.github.spud.tinystore.order.domain.statemachine.action.ApplyAutoCompletedAction;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder;
import com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics;
import org.springframework.beans.factory.ObjectProvider;
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
import lombok.RequiredArgsConstructor;

/**
 * 订单状态机配置 基于 Spring State Machine 实现订单状态流转控制
 */
@Slf4j
@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
public class OrderStateMachineConfig extends StateMachineConfigurerAdapter<CoreFlowStatus, OrderEvent> {

	private final OutboxEventService outboxEventService;
	private final ObjectProvider<AuditRecorder> auditRecorderProvider;
	private final OrderMetrics orderMetrics;

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
			.action(applyPaymentSucceededAction())
			.guard(canPayGuard())
			.and()
			// PENDING_PAYMENT cancellation paths
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.PAYMENT_FAILED)
			.action(applyUserCancelledAction())
			.guard(canCancelGuard())
			.and()
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.PAYMENT_TIMEOUT)
			.action(applyTimeoutCancelledAction())
			.guard(canCancelGuard())
			.and()
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.USER_CANCELLED)
			.action(applyUserCancelledAction())
			.guard(canCancelGuard())
			.and()
			.withExternal()
			.source(CoreFlowStatus.PENDING_PAYMENT)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.SYSTEM_CANCELLED)
			.action(applyUserCancelledAction())
			.guard(canCancelGuard())
			.and()
			// PAID -> ACCEPTED
			.withExternal()
			.source(CoreFlowStatus.PAID)
			.target(CoreFlowStatus.ACCEPTED)
			.event(OrderEvent.MERCHANT_ACCEPTED)
			.action(applyMerchantAcceptedAction())
			.and()
			// PAID cancellation (merchant)
			.withExternal()
			.source(CoreFlowStatus.PAID)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.MERCHANT_CANCELLED)
			.action(applyUserCancelledAction())
			.guard(canCancelGuard())
			.and()
			// ACCEPTED -> FULFILLING (goods shipped)
			.withExternal()
			.source(CoreFlowStatus.ACCEPTED)
			.target(CoreFlowStatus.FULFILLING)
			.event(OrderEvent.FULFILLMENT_STARTED)
			.action(applyGoodsShippedAction())
			.guard(canShipGuard())
			.and()
			// FULFILLING internal progress events
			.withInternal()
			.source(CoreFlowStatus.FULFILLING)
			.event(OrderEvent.GOODS_SHIPPED)
			.action(applyGoodsShippedAction())
			.guard(canConfirmDeliveryGuard())
			.and()
			.withInternal()
			.source(CoreFlowStatus.FULFILLING)
			.event(OrderEvent.GOODS_DELIVERED)
			.action(applyGoodsDeliveredAction())
			.guard(canConfirmDeliveryGuard())
			.and()
			// FULFILLING -> COMPLETED
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.COMPLETED)
			.event(OrderEvent.GOODS_RECEIVED)
			.action(applyGoodsReceivedAction())
			.guard(canReceiveGuard())
			.and()
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.COMPLETED)
			.event(OrderEvent.AUTO_RECEIVE_TIMEOUT)
			.action(applyAutoCompletedAction())
			.guard(canReceiveGuard())
			.and()
			// FULFILLING -> CANCELLED (reject / merchant / system)
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.GOODS_REJECTED)
			.action(applyUserCancelledAction())
			.guard(canCancelGuard())
			.and()
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.MERCHANT_CANCELLED)
			.action(applyUserCancelledAction())
			.guard(canCancelGuard())
			.and()
			.withExternal()
			.source(CoreFlowStatus.FULFILLING)
			.target(CoreFlowStatus.CANCELLED)
			.event(OrderEvent.SYSTEM_CANCELLED)
			.action(applyUserCancelledAction())
			.guard(canCancelGuard());

	}

	@Bean public TerminalStateGuard terminalStateGuard() { return new TerminalStateGuard(); }
	@Bean public PaymentPhaseGuard paymentPhaseGuard() { return new PaymentPhaseGuard(); }
	@Bean public FulfillmentMutexGuard fulfillmentMutexGuard() { return new FulfillmentMutexGuard(); }
	@Bean public CanPayGuard canPayGuard() { return new CanPayGuard(); }
	@Bean public CanShipGuard canShipGuard() { return new CanShipGuard(); }
	@Bean public CanConfirmDeliveryGuard canConfirmDeliveryGuard() { return new CanConfirmDeliveryGuard(); }
	@Bean public CanReceiveGuard canReceiveGuard() { return new CanReceiveGuard(); }
	@Bean public CanCancelGuard canCancelGuard() { return new CanCancelGuard(); }

	@Bean public ApplyPaymentSucceededAction applyPaymentSucceededAction() {
		return new ApplyPaymentSucceededAction(outboxEventService, auditRecorderProvider, orderMetrics);
	}
	@Bean public ApplyMerchantAcceptedAction applyMerchantAcceptedAction() {
		return new ApplyMerchantAcceptedAction(outboxEventService, auditRecorderProvider, orderMetrics);
	}
	@Bean public ApplyGoodsShippedAction applyGoodsShippedAction() {
		return new ApplyGoodsShippedAction(outboxEventService, auditRecorderProvider, orderMetrics);
	}
	@Bean public ApplyGoodsDeliveredAction applyGoodsDeliveredAction() {
		return new ApplyGoodsDeliveredAction(outboxEventService, auditRecorderProvider, orderMetrics);
	}
	@Bean public ApplyGoodsReceivedAction applyGoodsReceivedAction() {
		return new ApplyGoodsReceivedAction(outboxEventService, auditRecorderProvider, orderMetrics);
	}
	@Bean public ApplyUserCancelledAction applyUserCancelledAction() {
		return new ApplyUserCancelledAction(outboxEventService, auditRecorderProvider, orderMetrics);
	}
	@Bean public ApplyTimeoutCancelledAction applyTimeoutCancelledAction() {
		return new ApplyTimeoutCancelledAction(outboxEventService, auditRecorderProvider, orderMetrics);
	}
	@Bean public ApplyAutoCompletedAction applyAutoCompletedAction() {
		return new ApplyAutoCompletedAction(outboxEventService, auditRecorderProvider, orderMetrics);
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