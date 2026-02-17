package com.github.spud.tinystore.order.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.github.spud.tinystore.order.test.it.AbstractSpringBootOrderIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox Admin Endpoint Integration Test
 *
 * <p>Coverage:
 * - GET /internal/outbox/failed (查询失败事件)
 * - POST /internal/outbox/retry/{eventId} (重试失败事件)
 *
 * <p>Tests admin token authentication and outbox management operations.
 */
@DisplayName("Outbox Admin Endpoint Integration Tests")
class OutboxAdminEndpointIT extends AbstractSpringBootOrderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${tinystore.order.admin.token}")
    private String adminToken;

    @BeforeEach
    void cleanup() {
        outboxEventJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /internal/outbox/failed - without token returns 401")
    void testQueryFailedEvents_withoutToken_returns401() {
        // When: query without X-Admin-Token header
        ResponseEntity<String> response = restTemplate.exchange(
            "/internal/outbox/failed",
            HttpMethod.GET,
            null,
            String.class
        );

        // Then: returns 401 Unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHORIZED");
        assertThat(response.getBody()).contains("Invalid or missing X-Admin-Token header");
    }

    @Test
    @DisplayName("GET /internal/outbox/failed - with invalid token returns 401")
    void testQueryFailedEvents_withInvalidToken_returns401() {
        // Given: invalid token
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "invalid-token");

        // When: query with invalid token
        ResponseEntity<String> response = restTemplate.exchange(
            "/internal/outbox/failed",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: returns 401 Unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /internal/outbox/failed - with valid token returns failed events")
    void testQueryFailedEvents_withValidToken_returnsFailedEvents() throws Exception {
        // Given: insert FAILED outbox events
        OutboxEventEntity failedEvent1 = createFailedEvent("TRADE_PAID", "trade-001");
        OutboxEventEntity failedEvent2 = createFailedEvent("TRADE_CANCELED", "trade-002");
        outboxEventJpaRepository.save(failedEvent1);
        outboxEventJpaRepository.save(failedEvent2);

        // When: query with valid token
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
            "/internal/outbox/failed?limit=10",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: returns 200 with failed events
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        assertThat(jsonResponse.get("success").asBoolean()).isTrue();
        assertThat(jsonResponse.get("data").isArray()).isTrue();
        assertThat(jsonResponse.get("data").size()).isGreaterThanOrEqualTo(2);

        // Verify event details
        JsonNode firstEvent = jsonResponse.get("data").get(0);
        assertThat(firstEvent.has("eventId")).isTrue();
        assertThat(firstEvent.has("eventType")).isTrue();
        assertThat(firstEvent.has("aggregateId")).isTrue();
        assertThat(firstEvent.has("retryCount")).isTrue();
        assertThat(firstEvent.get("retryCount").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("GET /internal/outbox/failed - with eventType filter returns filtered events")
    void testQueryFailedEvents_withEventTypeFilter_returnsFilteredEvents() throws Exception {
        // Given: insert multiple event types
        OutboxEventEntity paidEvent = createFailedEvent("TRADE_PAID", "trade-003");
        OutboxEventEntity canceledEvent = createFailedEvent("TRADE_CANCELED", "trade-004");
        outboxEventJpaRepository.save(paidEvent);
        outboxEventJpaRepository.save(canceledEvent);

        // When: query with eventType filter
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
            "/internal/outbox/failed?limit=10&eventType=TRADE_PAID",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: returns only TRADE_PAID events
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        JsonNode data = jsonResponse.get("data");

        for (JsonNode event : data) {
            assertThat(event.get("eventType").asText()).isEqualTo("TRADE_PAID");
        }
    }

    @Test
    @DisplayName("GET /internal/outbox/failed - with since filter returns recent events")
    void testQueryFailedEvents_withSinceFilter_returnsRecentEvents() throws Exception {
        // Given: insert events at different times
        OutboxEventEntity oldEvent = createFailedEvent("TRADE_PAID", "trade-005");
        oldEvent.setCreatedAt(LocalDateTime.now().minusDays(10));
        outboxEventJpaRepository.save(oldEvent);

        OutboxEventEntity recentEvent = createFailedEvent("TRADE_PAID", "trade-006");
        recentEvent.setCreatedAt(LocalDateTime.now().minusDays(1));
        outboxEventJpaRepository.save(recentEvent);

        // When: query with since filter (5 days ago)
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", adminToken);

        String sinceDate = LocalDateTime.now().minusDays(5).toString();
        ResponseEntity<String> response = restTemplate.exchange(
            "/internal/outbox/failed?limit=10&since=" + sinceDate,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: returns only recent events
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        JsonNode data = jsonResponse.get("data");

        // Should contain recent event but not old event
        boolean hasRecentEvent = false;
        boolean hasOldEvent = false;

        for (JsonNode event : data) {
            if (event.get("aggregateId").asText().equals("trade-006")) {
                hasRecentEvent = true;
            }
            if (event.get("aggregateId").asText().equals("trade-005")) {
                hasOldEvent = true;
            }
        }

        assertThat(hasRecentEvent).isTrue();
        assertThat(hasOldEvent).isFalse();
    }

    @Test
    @DisplayName("POST /internal/outbox/retry/{eventId} - with FAILED event resets status to PENDING")
    void testRetryEvent_withFailedEvent_resetsStatusToPending() throws Exception {
        // Given: insert FAILED event
        OutboxEventEntity failedEvent = createFailedEvent("TRADE_PAID", "trade-007");
        outboxEventJpaRepository.save(failedEvent);

        String eventId = failedEvent.getEventId();

        // When: retry event
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
            "/internal/outbox/retry/" + eventId,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: returns 200 and event status is PENDING
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        assertThat(jsonResponse.get("success").asBoolean()).isTrue();
        assertThat(jsonResponse.get("data").asText()).contains("reset to PENDING");

        // Verify database state
        OutboxEventEntity updatedEvent = outboxEventJpaRepository.findByEventId(eventId).orElseThrow();
        assertThat(updatedEvent.getStatus()).isEqualTo("PENDING");
        assertThat(updatedEvent.getRetryCount()).isEqualTo(0);
        assertThat(updatedEvent.getLastError()).isNull();
    }

    @Test
    @DisplayName("POST /internal/outbox/retry/{eventId} - with PUBLISHED event returns skip message")
    void testRetryEvent_withPublishedEvent_returnsSkipMessage() throws Exception {
        // Given: insert PUBLISHED event
        OutboxEventEntity publishedEvent = createPublishedEvent("TRADE_PAID", "trade-008");
        outboxEventJpaRepository.save(publishedEvent);

        String eventId = publishedEvent.getEventId();

        // When: retry event
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
            "/internal/outbox/retry/" + eventId,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: returns 200 with skip message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        assertThat(jsonResponse.get("success").asBoolean()).isTrue();
        assertThat(jsonResponse.get("data").asText()).contains("already published");

        // Verify status unchanged
        OutboxEventEntity unchangedEvent = outboxEventJpaRepository.findByEventId(eventId).orElseThrow();
        assertThat(unchangedEvent.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("POST /internal/outbox/retry/{eventId} - with non-existent event returns 404")
    void testRetryEvent_withNonExistentEvent_returns404() throws Exception {
        // When: retry non-existent event
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
            "/internal/outbox/retry/non-existent-event-id",
            HttpMethod.POST,
            new HttpEntity<>(headers),
            String.class
        );

        // Then: returns 404
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode jsonResponse = objectMapper.readTree(response.getBody());
        assertThat(jsonResponse.get("success").asBoolean()).isFalse();
        assertThat(jsonResponse.get("errorMessage").asText()).contains("not found");
    }

    // Helper methods

    private OutboxEventEntity createFailedEvent(String eventType, String aggregateId) {
        return OutboxEventEntity.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(eventType)
            .aggregateType("Trade")
            .aggregateId(aggregateId)
            .payloadJson("{}")
            .status("FAILED")
            .retryCount(3)
            .lastError("Kafka send timeout")
            .traceId(UUID.randomUUID().toString())
            .createdAt(LocalDateTime.now().minusHours(1))
            .publishedAt(null)
            .build();
    }

    private OutboxEventEntity createPublishedEvent(String eventType, String aggregateId) {
        return OutboxEventEntity.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(eventType)
            .aggregateType("Trade")
            .aggregateId(aggregateId)
            .payloadJson("{}")
            .status("PUBLISHED")
            .retryCount(0)
            .lastError(null)
            .traceId(UUID.randomUUID().toString())
            .createdAt(LocalDateTime.now().minusHours(2))
            .publishedAt(LocalDateTime.now().minusHours(1))
            .build();
    }
}
