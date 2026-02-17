package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.infrastructure.event.outbox.OutboxEventService;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.github.spud.tinystore.order.interfaces.dto.response.OrderHttpResponse;
import com.github.spud.tinystore.order.interfaces.dto.response.OutboxFailedEventDTO;
import com.github.spud.tinystore.order.interfaces.error.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Admin REST Controller
 *
 * <p>Internal admin endpoints for monitoring and managing outbox events.
 * Path prefix: /internal/outbox
 */
@Slf4j
@RestController
@RequestMapping("/internal/outbox")
public class OutboxAdminController {

    @Autowired
    private OutboxEventService outboxEventService;

    /**
     * GET /internal/outbox/failed - Query failed outbox events
     *
     * @param limit Maximum number of events to return (default 100, max 500)
     * @param since Filter events created after this datetime (ISO format, optional)
     * @param eventType Filter by event type (optional)
     * @return List of failed events
     */
    @GetMapping("/failed")
    public OrderHttpResponse<List<OutboxFailedEventDTO>> queryFailedEvents(
        @RequestParam(defaultValue = "100") int limit,
        @RequestParam(required = false) String since,
        @RequestParam(required = false) String eventType) {

        try {
            // Validate and normalize limit
            if (limit < 1 || limit > 500) {
                return OrderHttpResponse.fail(400, "Limit must be between 1 and 500");
            }

            // Parse since parameter (default to 30 days ago if not provided)
            LocalDateTime sinceDateTime = (since != null && !since.isBlank())
                ? LocalDateTime.parse(since)
                : LocalDateTime.now().minusDays(30);

            // Query failed events
            List<OutboxEventEntity> failedEvents = outboxEventService.findFailedEvents(
                limit, sinceDateTime, eventType);

            // Convert to DTO
            List<OutboxFailedEventDTO> dtos = failedEvents.stream()
                .map(event -> new OutboxFailedEventDTO(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getRetryCount() != null ? event.getRetryCount() : 0,
                    event.getLastError(),
                    event.getCreatedAt(),
                    event.getPublishedAt() // Using publishedAt as lastFailedAt
                ))
                .toList();

            log.info("Queried failed outbox events: count={}, limit={}, eventType={}, since={}",
                dtos.size(), limit, eventType, sinceDateTime);

            return OrderHttpResponse.ok(dtos);

        } catch (Exception e) {
            log.error("Failed to query failed outbox events", e);
            return OrderHttpResponse.fail(500, "Failed to query failed events: " + e.getMessage());
        }
    }

    /**
     * POST /internal/outbox/retry/{eventId} - Retry a failed outbox event
     *
     * @param eventId Event ID to retry
     * @return Success message or skip message if already published
     */
    @PostMapping("/retry/{eventId}")
    public OrderHttpResponse<String> retryEvent(@PathVariable String eventId) {

        try {
            outboxEventService.retryEvent(eventId);

            log.info("Outbox event retry initiated: eventId={}", eventId);
            return OrderHttpResponse.ok("Event reset to PENDING for retry");

        } catch (IllegalStateException e) {
            if (e.getMessage().contains("not found")) {
                throw new NotFoundException("Outbox event not found: " + eventId);
            }
            // Event already published (handled in service layer)
            return OrderHttpResponse.ok("Event already published, skip retry");

        } catch (Exception e) {
            log.error("Failed to retry outbox event: eventId={}", eventId, e);
            return OrderHttpResponse.fail(500, "Failed to retry event: " + e.getMessage());
        }
    }
}
