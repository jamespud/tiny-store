package com.github.spud.tinystore.order.test.it;

import com.github.spud.tinystore.order.OrderApplication;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventPublisher;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OutboxEventJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpringBoot 集成测试基类
 * 提供 Outbox 同步化覆盖 + 验证工具方法
 */
@SpringBootTest(
    classes = {OrderApplication.class, AbstractSpringBootOrderIT.TestOverrides.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public abstract class AbstractSpringBootOrderIT extends AbstractOrderIT {

    @Autowired
    protected OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    protected OutboxEventService outboxEventService;

    /**
     * 断言 Outbox 事件存在
     */
    protected void assertOutboxEventExists(String aggregateType, String aggregateId, String eventType) {
        List<OutboxEventEntity> events = outboxEventJpaRepository.findByAggregateIdOrderByCreatedAtAsc(aggregateId);
        
        Optional<OutboxEventEntity> found = events.stream()
            .filter(e -> e.getAggregateType().equals(aggregateType) && e.getEventType().equals(eventType))
            .findFirst();
        
        assertThat(found)
            .as("Outbox event should exist: aggregateType=%s, aggregateId=%s, eventType=%s", 
                aggregateType, aggregateId, eventType)
            .isPresent();
    }

    /**
     * 断言 Outbox 事件已发布
     */
    protected void assertOutboxEventPublished(String aggregateType, String aggregateId, String eventType) {
        List<OutboxEventEntity> events = outboxEventJpaRepository.findByAggregateIdOrderByCreatedAtAsc(aggregateId);
        
        Optional<OutboxEventEntity> found = events.stream()
            .filter(e -> e.getAggregateType().equals(aggregateType) && e.getEventType().equals(eventType))
            .findFirst();
        
        assertThat(found)
            .as("Outbox event should exist: aggregateType=%s, aggregateId=%s, eventType=%s", 
                aggregateType, aggregateId, eventType)
            .isPresent();
        
        assertThat(found.get().getStatus())
            .as("Outbox event should be PUBLISHED")
            .isEqualTo("PUBLISHED");
    }

    /**
     * 测试配置类：同步化 Outbox 发布
     */
    @TestConfiguration
    public static class TestOverrides {

        @Bean
        @Primary
        public OutboxEventPublisher testOutboxEventPublisher(OutboxEventService outboxEventService) {
            return new OutboxEventPublisher() {
                @Override
                public void publishEvent(OutboxEventEntity event) {
                    // 同步化：直接标记为已发布，不走 Kafka
                    outboxEventService.markAsPublished(event.getEventId());
                }

                @Override
                public void publishEvents(List<OutboxEventEntity> events) {
                    // 同步化：批量标记为已发布，不走 Kafka
                    events.forEach(e -> outboxEventService.markAsPublished(e.getEventId()));
                }
            };
        }
    }
}
