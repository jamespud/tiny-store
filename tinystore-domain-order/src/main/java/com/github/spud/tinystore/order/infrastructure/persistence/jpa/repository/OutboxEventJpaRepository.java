package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
