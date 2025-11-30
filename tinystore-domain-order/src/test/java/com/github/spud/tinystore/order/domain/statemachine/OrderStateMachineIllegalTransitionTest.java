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

/**
 * 非法/过早事件发送测试：确保状态保持不变且不发生意外跃迁。
 */
@SpringBootTest(classes = {OrderStateMachineConfig.class, OrderStateMachineIllegalTransitionTest.TestBeans.class})
class OrderStateMachineIllegalTransitionTest {

    @Autowired
    private StateMachineFactory<CoreFlowStatus, OrderEvent> factory;

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
    @DisplayName("过早发送 GOODS_SHIPPED 不应从 ACCEPTED 跃迁")
    void goodsShippedTooEarlyIgnored() {
        StateMachine<CoreFlowStatus, OrderEvent> sm = factory.getStateMachine();
        sm.startReactively().block();
        send(sm, OrderEvent.PAYMENT_SUCCEEDED); // -> PAID
        send(sm, OrderEvent.MERCHANT_ACCEPTED); // -> ACCEPTED
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.ACCEPTED);
        // 过早发送发货事件，应被忽略，保持 ACCEPTED
        send(sm, OrderEvent.GOODS_SHIPPED);
        assertThat(sm.getState().getId()).isEqualTo(CoreFlowStatus.ACCEPTED);
    }
}
