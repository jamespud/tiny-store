package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * OutboxEvent JPA Repository
 */
@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    Optional<OutboxEventEntity> findByEventId(String eventId);

    /**
     * 读取待发布事件并加行锁（FOR UPDATE SKIP LOCKED）。
     *
     * <p>注意：行锁只在事务内有效。调用方 <b>必须</b> 在事务中调用，并在同一事务里把行改成
     * PROCESSING —— 否则锁在语句结束时就释放，多副本仍会取到同一批行（这正是 C3 的根因）。
     * 请使用 {@code OutboxEventService.claimPendingEvents(...)}，不要直接调用本方法。
     */
    @Query(value = "SELECT * FROM tinystore_order.order_outbox WHERE status = :status "
            + "AND (next_attempt_at IS NULL OR next_attempt_at <= now()) "
            + "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventEntity> findPendingEvents(@Param("status") String status, @Param("limit") int limit);

    /**
     * 回收僵尸认领：PROCESSING 且认领时间早于阈值的事件重新回到 PENDING。
     *
     * <p>覆盖"认领成功 → JVM 崩溃 → 永远 PROCESSING"的场景。
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE OutboxEventEntity e SET e.status = 'PENDING', e.claimedBy = NULL, e.claimedAt = NULL, "
            + "e.claimToken = NULL "
            + "WHERE e.status = 'PROCESSING' AND e.claimedAt < :claimedBefore")
    int reclaimStaleClaims(@Param("claimedBefore") LocalDateTime claimedBefore);

    /**
     * 批量标记已发布（替代逐条 SELECT+UPDATE，一次往返）。
     * 自定义 @Modifying 方法不自动加事务——必须显式 @Transactional（否则 Hibernate 拒绝批量 UPDATE）。
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE OutboxEventEntity e SET e.status = 'PUBLISHED', e.publishedAt = :publishedAt "
            // Fenced by the caller's lease when it has one; rows that are not under a live lease (published
            // through the admin path or a direct call) still complete, but a row another worker currently
            // holds can never be touched by a stale owner.
            + "WHERE e.eventId IN :eventIds AND (e.status <> 'PROCESSING' OR e.claimToken = :claimToken)")
    int markAsPublishedBatch(@Param("eventIds") List<String> eventIds,
                             @Param("publishedAt") LocalDateTime publishedAt,
                             @Param("claimToken") String claimToken);

    /**
     * Fenced failure update (review P1-2): only the owner of the current lease may move the row.
     *
     * @return 1 when this worker still held the lease, 0 when the claim was reclaimed meanwhile
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE OutboxEventEntity e SET e.retryCount = coalesce(e.retryCount, 0) + 1, "
            + "e.lastError = :error, e.status = :status, e.nextAttemptAt = :nextAttemptAt, "
            + "e.claimedBy = NULL, e.claimedAt = NULL, e.claimToken = NULL "
            + "WHERE e.eventId = :eventId AND (e.status <> 'PROCESSING' OR e.claimToken = :claimToken)")
    int markAsFailedIfOwned(@Param("eventId") String eventId,
                            @Param("claimToken") String claimToken,
                            @Param("error") String error,
                            @Param("status") String status,
                            @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

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
