package com.github.spud.tinystore.order.infrastructure.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.OutboxEventJpaRepository;
import com.github.spud.tinystore.order.test.it.AbstractOrderIT;

/**
 * Review P1-2: a claim is a lease, and completing an event must be *fenced* by that lease.
 *
 * <p>Without a fencing token, a worker that stalled past the claim timeout could still write
 * PUBLISHED/FAILED after another worker had reclaimed the same event -- the old owner silently overrode the
 * live lease. These tests drive the real queries against PostgreSQL: after a reclaim, the previous token must
 * affect zero rows and leave the new owner's state untouched.
 */
@DataJpaTest
@Import({ OutboxEventService.class, OutboxClaimFencingIT.ObjectMapperConfig.class })
@DisplayName("outbox claim fencing (P1-2)")
class OutboxClaimFencingIT extends AbstractOrderIT {

    @TestConfiguration
    static class ObjectMapperConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Test
    @DisplayName("a stale owner cannot publish or fail an event another worker reclaimed")
    void staleOwnerIsFencedOut() {
        OutboxEventEntity event = outboxEventJpaRepository.save(OutboxEventEntity.builder()
            .eventId("evt-fencing-1")
            .eventType("ORDER_CREATED")
            .aggregateType("ORDER")
            .aggregateId("order-1")
            .payloadJson("{}")
            .status("PENDING")
            .retryCount(0)
            .createdAt(LocalDateTime.now())
            .build());
        String eventId = event.getEventId();

        // A claims the event, then stalls past the claim timeout.
        List<OutboxEventEntity> claimedByA = outboxEventService.claimPendingEvents(10, "instance-A");
        assertThat(claimedByA).hasSize(1);
        String tokenA = claimedByA.get(0).getClaimToken();
        assertThat(tokenA).isNotBlank();

        assertThat(outboxEventService.reclaimStaleClaims(Duration.ZERO)).isEqualTo(1);

        // B reclaims it with a fresh token.
        List<OutboxEventEntity> claimedByB = outboxEventService.claimPendingEvents(10, "instance-B");
        assertThat(claimedByB).hasSize(1);
        String tokenB = claimedByB.get(0).getClaimToken();
        assertThat(tokenB).isNotEqualTo(tokenA);

        // A wakes up and tries to complete the work it no longer owns: nothing may change.
        outboxEventService.markAsPublishedBatch(List.of(eventId), tokenA);
        OutboxEventEntity afterStalePublish = outboxEventJpaRepository.findByEventId(eventId).orElseThrow();
        assertThat(afterStalePublish.getStatus())
            .withFailMessage("a reclaimed lease must not be completed by its previous owner")
            .isEqualTo("PROCESSING");
        assertThat(afterStalePublish.getClaimToken()).isEqualTo(tokenB);

        outboxEventService.markAsFailed(eventId, tokenA, "stale worker failure", null);
        OutboxEventEntity afterStaleFailure = outboxEventJpaRepository.findByEventId(eventId).orElseThrow();
        assertThat(afterStaleFailure.getStatus()).isEqualTo("PROCESSING");
        assertThat(afterStaleFailure.getRetryCount()).isZero();
        assertThat(afterStaleFailure.getClaimToken()).isEqualTo(tokenB);

        // The current owner can complete normally.
        outboxEventService.markAsPublishedBatch(List.of(eventId), tokenB);
        OutboxEventEntity afterOwnerPublish = outboxEventJpaRepository.findByEventId(eventId).orElseThrow();
        assertThat(afterOwnerPublish.getStatus()).isEqualTo("PUBLISHED");
        assertThat(afterOwnerPublish.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("a transport failure by the current owner still returns the event to PENDING with backoff")
    void currentOwnerFailureStillPends() {
        outboxEventJpaRepository.save(OutboxEventEntity.builder()
            .eventId("evt-fencing-2")
            .eventType("ORDER_CREATED")
            .aggregateType("ORDER")
            .aggregateId("order-2")
            .payloadJson("{}")
            .status("PENDING")
            .retryCount(0)
            .createdAt(LocalDateTime.now())
            .build());

        List<OutboxEventEntity> claimed = outboxEventService.claimPendingEvents(10, "instance-A");
        String token = claimed.get(0).getClaimToken();

        outboxEventService.markAsFailed("evt-fencing-2", token, "kafka down", null);

        OutboxEventEntity after = outboxEventJpaRepository.findByEventId("evt-fencing-2").orElseThrow();
        assertThat(after.getStatus()).isEqualTo("PENDING");
        assertThat(after.getRetryCount()).isEqualTo(1);
        assertThat(after.getNextAttemptAt()).isNotNull();
        assertThat(after.getClaimToken()).isNull();
    }

    @Test
    @DisplayName("round-3 P1: a stale owner cannot re-pend an event the new owner already PUBLISHED")
    void staleOwnerCannotReopenAPublishedEvent() {
        outboxEventJpaRepository.save(OutboxEventEntity.builder()
            .eventId("evt-fencing-3")
            .eventType("ORDER_CREATED")
            .aggregateType("ORDER")
            .aggregateId("order-3")
            .payloadJson("{}")
            .status("PENDING")
            .retryCount(0)
            .createdAt(LocalDateTime.now())
            .build());

        // A claims, stalls past the timeout; B reclaims and publishes successfully.
        List<OutboxEventEntity> claimedByA = outboxEventService.claimPendingEvents(10, "instance-A");
        String tokenA = claimedByA.get(0).getClaimToken();
        outboxEventService.reclaimStaleClaims(Duration.ZERO);
        List<OutboxEventEntity> claimedByB = outboxEventService.claimPendingEvents(10, "instance-B");
        String tokenB = claimedByB.get(0).getClaimToken();

        outboxEventService.markAsPublishedBatch(List.of("evt-fencing-3"), tokenB);
        OutboxEventEntity afterOwnerPublish =
            outboxEventJpaRepository.findByEventId("evt-fencing-3").orElseThrow();
        assertThat(afterOwnerPublish.getStatus()).isEqualTo("PUBLISHED");
        assertThat(afterOwnerPublish.getClaimToken()).isNull();

        // A now wakes up and reports the failure of its long-gone send. The event is already PUBLISHED, so
        // this must be a no-op -- the previous "status <> PROCESSING" fence let A re-pend it here, and the
        // scheduler would then publish the same event a second time.
        outboxEventService.markAsFailed("evt-fencing-3", tokenA, "stale worker failure", null);

        OutboxEventEntity afterStaleFailure =
            outboxEventJpaRepository.findByEventId("evt-fencing-3").orElseThrow();
        assertThat(afterStaleFailure.getStatus())
            .withFailMessage("a stale owner must not re-open an event the current owner already published")
            .isEqualTo("PUBLISHED");
        assertThat(afterStaleFailure.getRetryCount()).isZero();
    }
}
