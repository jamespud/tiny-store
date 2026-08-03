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

    /**
     * 分片抢占待发布事件：FOR UPDATE SKIP LOCKED 保证多实例发布会各自抢占不同批次。
     */
    @Query(value = "SELECT * FROM tinystore_order.order_outbox WHERE status = :status "
            + "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventEntity> findPendingEvents(@Param("status") String status, @Param("limit") int limit);

    /**
     * 批量标记已发布（替代逐条 SELECT+UPDATE，一次往返）。
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.status = 'PUBLISHED', e.publishedAt = :publishedAt "
            + "WHERE e.eventId IN :eventIds")
    int markAsPublishedBatch(@Param("eventIds") List<String> eventIds,
                             @Param("publishedAt") LocalDateTime publishedAt);

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
