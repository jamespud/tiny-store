package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

/**
 * @author Spud
 * @date 2025/9/9
 */
@Repository
public interface OutboxRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Object> findPendingEventsWithLock(
            Object status,
            LocalDateTime now,
            org.springframework.data.domain.Pageable pageable
    );

    void save(Object o);
}
