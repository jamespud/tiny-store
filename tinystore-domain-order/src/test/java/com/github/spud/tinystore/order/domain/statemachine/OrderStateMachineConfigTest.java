package com.github.spud.tinystore.order.domain.statemachine;

import com.github.spud.tinystore.order.domain.statemachine.event.OrderEvent;
import com.github.spud.tinystore.order.domain.statemachine.status.CoreFlowStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {OrderStateMachineConfig.class, OrderStateMachineConfigTest.TestBeans.class})
class OrderStateMachineConfigTest {

    @Autowired
    private StateMachineFactory<CoreFlowStatus, OrderEvent> factory;

    /**
     * 提供状态机配置所需的依赖 Bean 的最小测试桩，避免 ApplicationContext 加载失败。
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class TestBeans {
        @org.springframework.context.annotation.Bean
        com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService outboxEventService() {
            return org.mockito.Mockito.mock(com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService.class);
        }
        @org.springframework.context.annotation.Bean
        com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics orderMetrics() {
            return org.mockito.Mockito.mock(com.github.spud.tinystore.order.infrastructure.metrics.OrderMetrics.class);
        }
        @org.springframework.context.annotation.Bean
        org.springframework.beans.factory.ObjectProvider<com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder> auditRecorderProvider() {
            com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder mock = org.mockito.Mockito.mock(com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder.class);
            return new org.springframework.beans.factory.ObjectProvider<>() {
                @Override public com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder getObject(Object... args){ return mock; }
                @Override public com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder getIfAvailable(){ return mock; }
                @Override public com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder getIfAvailable(java.util.function.Supplier<com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder> supplier){ return mock; }
                @Override public com.github.spud.tinystore.order.infrastructure.audit.AuditRecorder getObject(){ return mock; }
            };
        }
    }

    private void send(StateMachine<CoreFlowStatus, OrderEvent> sm, OrderEvent e) {
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(e).build())).collectList().block();
    }

    @Test
    @DisplayName("happy path: payment -> acceptance -> fulfillment -> receipt")
    void happyPathToCompleted() {
    StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
    sm.startReactively().block();
    assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.PENDING_PAYMENT);

    send(sm, OrderEvent.PAYMENT_SUCCEEDED);
    assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.PAID);

    send(sm, OrderEvent.MERCHANT_ACCEPTED);
    assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.ACCEPTED);

    send(sm, OrderEvent.FULFILLMENT_STARTED);
    assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.FULFILLING);

    // internal progress events should not change state
    send(sm, OrderEvent.GOODS_SHIPPED);
    assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.FULFILLING);
    send(sm, OrderEvent.GOODS_DELIVERED);
    assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.FULFILLING);

    send(sm, OrderEvent.GOODS_RECEIVED);
    assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.COMPLETED);
    }

    @Test
    @DisplayName("payment failure leads to cancellation")
    void paymentFailedCancels() {
    StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
    sm.startReactively().block();
    send(sm, OrderEvent.PAYMENT_FAILED);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.CANCELLED);
    }

    @Test
    @DisplayName("timeout in payment leads to cancellation")
    void paymentTimeoutCancels() {
    StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
    sm.startReactively().block();
    send(sm, OrderEvent.PAYMENT_TIMEOUT);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.CANCELLED);
    }

    @Test
    @DisplayName("merchant cancellation from fulfilling")
    void merchantCancelsDuringFulfillment() {
    StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
    sm.startReactively().block();
    send(sm, OrderEvent.PAYMENT_SUCCEEDED);
    send(sm, OrderEvent.MERCHANT_ACCEPTED);
    send(sm, OrderEvent.FULFILLMENT_STARTED);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.FULFILLING);
    send(sm, OrderEvent.MERCHANT_CANCELLED);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.CANCELLED);
    }

    @Test
    @DisplayName("auto receive timeout completes order")
    void autoReceiveTimeoutCompletes() {
    StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
    sm.startReactively().block();
    send(sm, OrderEvent.PAYMENT_SUCCEEDED);
    send(sm, OrderEvent.MERCHANT_ACCEPTED);
    send(sm, OrderEvent.FULFILLMENT_STARTED);
    send(sm, OrderEvent.AUTO_RECEIVE_TIMEOUT);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.COMPLETED);
    }

    @Test
    @DisplayName("goods rejected cancels order")
    void goodsRejectedCancels() {
    StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
    sm.startReactively().block();
    send(sm, OrderEvent.PAYMENT_SUCCEEDED);
    send(sm, OrderEvent.MERCHANT_ACCEPTED);
    send(sm, OrderEvent.FULFILLMENT_STARTED);
    send(sm, OrderEvent.GOODS_REJECTED);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.CANCELLED);
    }

    @Test
    @DisplayName("illegal event after completion is ignored")
    void eventIgnoredAfterCompletion() {
        StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
        sm.startReactively().block();
        send(sm, OrderEvent.PAYMENT_SUCCEEDED);
        send(sm, OrderEvent.MERCHANT_ACCEPTED);
        send(sm, OrderEvent.FULFILLMENT_STARTED);
        send(sm, OrderEvent.GOODS_RECEIVED);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.COMPLETED);
        // sending a cancellation should not change completed terminal state
        send(sm, OrderEvent.USER_CANCELLED);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.COMPLETED);
    }

    @Test
    @DisplayName("user cancel in pending payment leads to cancelled")
    void userCancel_in_pendingPayment_to_cancelled() {
        StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
        sm.startReactively().block();
        send(sm, OrderEvent.USER_CANCELLED);
        org.assertj.core.api.Assertions.assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.CANCELLED);
    }
}
