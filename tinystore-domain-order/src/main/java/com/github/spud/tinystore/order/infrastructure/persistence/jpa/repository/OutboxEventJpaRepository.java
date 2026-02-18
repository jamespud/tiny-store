package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * OutboxEvent JPA Repository
 */
@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    Optional<OutboxEventEntity> findByEventId(String eventId);

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = :status ORDER BY e.createdAt ASC LIMIT :limit")
    List<OutboxEventEntity> findPendingEvents(@Param("status") String status, @Param("limit") int limit);

    List<OutboxEventEntity> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);

    /**
     * Find failed outbox events created after a specific time
     *
     * @param status Event status (typically "FAILED")
     * @param since Starting datetime for the query
     * @param pageable Pagination parameters
     * @return List of failed events ordered by creation time descending
     */
    List<OutboxEventEntity> findByStatusAndCreatedAtAfterOrderByCreatedAtDesc(
        String status,
        LocalDateTime since,
        Pageable pageable
    );

    /**
     * Find failed outbox events of a specific type created after a specific time
     *
     * @param status Event status (typically "FAILED")
     * @param eventType Event type filter
     * @param since Starting datetime for the query
     * @param pageable Pagination parameters
     * @return List of failed events ordered by creation time descending
     */
    List<OutboxEventEntity> findByStatusAndEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
        String status,
        String eventType,
        LocalDateTime since,
        Pageable pageable
    );

    /**
     * Delete published outbox events that were published before the specified time
     *
     * @param publishedBefore Cutoff time - events published before this will be deleted
     * @return Number of deleted events
     */
    @Modifying
    @Query("DELETE FROM OutboxEventEntity e WHERE e.status = 'PUBLISHED' AND e.publishedAt IS NOT NULL AND e.publishedAt < :publishedBefore")
    int deletePublishedEventsBefore(@Param("publishedBefore") LocalDateTime publishedBefore);
}
