package com.github.spud.tinystore.product.infrastructure.outbox;

import com.github.spud.tinystore.product.domain.event.*;
import org.springframework.stereotype.Component;

/**
 * OutboxServiceBridge - Bridges domain events to Outbox and Kafka publishing
 * 
 * Responsibilities:
 * 1. Serialize domain events to JSONB
 * 2. Write to outbox table in same transaction as domain changes
 * 3. Coordinate with background worker for Kafka publishing
 * 
 * Integration points:
 * - Uses tinystore-library-infrastructure's Outbox components (if available)
 * - Or implements standalone outbox write + polling worker
 * 
 * Configuration:
 * - Enabled via feature.outbox.enabled
 * - Falls back to LoggingEventPublisher when disabled
 * 
 * TODO: Implement integration with:
 * - OutboxRepository for persistence
 * - JSON serialization (Jackson/Gson)
 * - Kafka producer (via Spring Kafka)
 */
@Component
public class OutboxServiceBridge {
    
    /**
     * Publish domain event via outbox pattern
     * 
     * @param event Domain event to publish
     * @throws UnsupportedOperationException until implementation complete
     */
    public void publish(Object event) {
        // TODO: Implement outbox publishing:
        // 1. Extract event metadata (type, aggregate ID, tenant)
        // 2. Serialize event payload to JSON
        // 3. Create OutboxEntity
        // 4. Save to outbox table
        // 5. Background worker will poll and publish to Kafka
        throw new UnsupportedOperationException("OutboxServiceBridge.publish not yet implemented");
    }
    
    /**
     * Publish ProductCreatedEvent
     * 
     * @param event ProductCreatedEvent
     */
    public void publish(ProductCreatedEvent event) {
        publish((Object) event);
    }
    
    /**
     * Publish PriceChangedEvent
     * 
     * @param event PriceChangedEvent
     */
    public void publish(PriceChangedEvent event) {
        publish((Object) event);
    }
    
    /**
     * Publish ProductPublishedEvent
     * 
     * @param event ProductPublishedEvent
     */
    public void publish(ProductPublishedEvent event) {
        publish((Object) event);
    }
}
