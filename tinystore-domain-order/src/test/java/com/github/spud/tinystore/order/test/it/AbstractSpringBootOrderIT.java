package com.github.spud.tinystore.order.test.it;

import com.github.spud.tinystore.order.OrderApplication;
import com.github.spud.tinystore.order.infrastructure.acl.InventoryClient;
import com.github.spud.tinystore.order.infrastructure.acl.PromotionClient;
import com.github.spud.tinystore.order.infrastructure.acl.dto.*;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventPublisher;
import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SpringBoot 集成测试基类
 * 提供 Outbox 同步化覆盖 + 验证工具方法
 */
@SpringBootTest(
    classes = {OrderApplication.class, AbstractSpringBootOrderIT.TestOverrides.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
    }
)
public abstract class AbstractSpringBootOrderIT extends AbstractOrderIT {

    @Autowired
    protected OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    protected OutboxEventService outboxEventService;

    @MockBean
    protected PromotionClient promotionClient;

    @MockBean
    protected InventoryClient inventoryClient;

    /**
     * 设置默认 Mock 行为，让外部依赖在测试中返回成功
     */
    @BeforeEach
    void setupDefaultMocks() {
        // Promotion Client - quote 默认成功
        PromotionQuoteResponse quoteResponse = PromotionQuoteResponse.builder()
            .status(PromotionQuoteResponse.CheckoutResultStatus.OK)
            .quoteId("test-quote-id")
            .expiresAtEpochMs(System.currentTimeMillis() + 300000L)
            .snapshot(PromotionQuoteResponse.PricingSnapshot.builder()
                .itemsTotalCents(10000L)
                .promotionDiscountTotalCents(0L)
                .couponDiscountTotalCents(0L)
                .shippingFeeCents(0L)
                .payableCents(10000L)
                .lines(Collections.emptyList())
                .appliedBenefits(Collections.emptyList())
                .version(PromotionQuoteResponse.PricingSnapshot.SnapshotVersion.builder()
                    .inputHash("test-input-hash-" + System.currentTimeMillis())
                    .build())
                .build())
            .changeReasons(Collections.emptyList())
            .build();
        when(promotionClient.quote(any(), any())).thenReturn(quoteResponse);

        // Promotion Client - commit 默认成功
        PromotionCommitResponse commitResponse = PromotionCommitResponse.builder()
            .status(PromotionQuoteResponse.CheckoutResultStatus.OK)
            .finalQuoteId("test-final-quote-id")
            .message("Commit successful")
            .build();
        when(promotionClient.commit(any(), any())).thenReturn(commitResponse);

        // Inventory Client - preOccupy 默认成功
        InventoryPreOccupyResponse preOccupyResponse = InventoryPreOccupyResponse.builder()
            .success(true)
            .preOccupyIds(Collections.singletonList("test-occupy-id"))
            .lackSkuIds(Collections.emptyList())
            .expiresAtEpochMs(System.currentTimeMillis() + 300000L)
            .message("PreOccupy successful")
            .build();
        when(inventoryClient.preOccupy(any(), any())).thenReturn(preOccupyResponse);

        // Inventory Client - commit 默认成功
        InventoryCommitResponse inventoryCommitResponse = InventoryCommitResponse.builder()
            .success(true)
            .message("Inventory committed")
            .build();
        when(inventoryClient.commit(any(), any())).thenReturn(inventoryCommitResponse);
    }

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
