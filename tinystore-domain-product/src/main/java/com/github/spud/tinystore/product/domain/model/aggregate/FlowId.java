package com.github.spud.tinystore.product.domain.model.aggregate;

import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;

import java.time.LocalDateTime;

// Placeholder identifier
public final class FlowId {
    private final String id;
    public FlowId(String id) { this.id = id; }
}

// Simple status record placeholder
class StatusRecord {
    public StatusRecord(Object from, Object to, Object operator, LocalDateTime at, String comment) {}
}

// Operator placeholder
class Operator {
    private String operatorId;
}

// Simple exception placeholders
class InvalidStateException extends RuntimeException { public InvalidStateException(String m){ super(m);} }
class InvalidOperationException extends RuntimeException { public InvalidOperationException(String m){ super(m);} }

// Minimal domain event publisher stub
class DomainEventPublisher {
    public static void publish(Object event) {
        // no-op placeholder
    }
}

// Event placeholders
class ProductSubmittedEvent { public ProductSubmittedEvent(ProductId id){} }
class ProductApprovedEvent { public ProductApprovedEvent(ProductId id){} }

// Category placeholders
class SpecificationTemplate {}
enum CategoryStatus { ENABLED, DISABLED }
