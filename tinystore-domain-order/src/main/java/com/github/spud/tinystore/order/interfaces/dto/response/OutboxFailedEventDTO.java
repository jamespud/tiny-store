package com.github.spud.tinystore.order.interfaces.dto.response;

import java.time.LocalDateTime;

/**
 * Outbox Failed Event Data Transfer Object
 *
 * <p>Used to expose failed outbox events for admin monitoring and retry operations.
 */
public record OutboxFailedEventDTO(
    String eventId,
    String eventType,
    String aggregateId,
    int retryCount,
    String lastError,
    LocalDateTime createdAt,
    LocalDateTime lastFailedAt
) {
}
