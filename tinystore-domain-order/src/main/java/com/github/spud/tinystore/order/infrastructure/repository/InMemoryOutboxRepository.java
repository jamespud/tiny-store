package com.github.spud.tinystore.order.infrastructure.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.order.domain.model.Outbox;
import com.github.spud.tinystore.order.domain.repository.OutboxRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Outbox 仓储的内存实现
 * 用于开发阶段，生产环境应使用数据库实现
 * 
 * @author Spud
 * @date 2025/9/22
 */
@Slf4j
@Repository
public class InMemoryOutboxRepository implements OutboxRepository {
    
    private final Map<UUID, Outbox> storage = new ConcurrentHashMap<>();
    
    @Override
    public void save(Outbox outbox) {
        storage.put(outbox.getEventId(), outbox);
        log.debug("Saved outbox event: eventId={}, eventType={}", outbox.getEventId(), outbox.getEventType());
    }
    
    @Override
    public List<Outbox> findPendingEvents(int limit) {
        return storage.values().stream()
            .filter(event -> event.getStatus() == Outbox.PublishStatus.PENDING)
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Outbox> findEventsForRetry(Instant beforeTime, int limit) {
        return storage.values().stream()
            .filter(event -> event.getStatus() == Outbox.PublishStatus.FAILED)
            .filter(event -> event.getNextRetryAt() != null && event.getNextRetryAt().isBefore(beforeTime))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public void updateStatus(UUID eventId, Outbox.PublishStatus status, 
                           Integer retryCount, Instant nextRetryAt, String errorMessage) {
        Outbox event = storage.get(eventId);
        if (event != null) {
            event.setStatus(status);
            event.setRetryCount(retryCount);
            event.setNextRetryAt(nextRetryAt);
            event.setErrorMessage(errorMessage);
            log.debug("Updated outbox event status: eventId={}, status={}, retryCount={}", 
                eventId, status, retryCount);
        }
    }
    
    @Override
    public void markAsPublished(UUID eventId) {
        Outbox event = storage.get(eventId);
        if (event != null) {
            event.setStatus(Outbox.PublishStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            log.debug("Marked outbox event as published: eventId={}", eventId);
        }
    }
    
    @Override
    public int deletePublishedEventsBefore(Instant beforeTime) {
        List<UUID> toDelete = storage.values().stream()
            .filter(event -> event.getStatus() == Outbox.PublishStatus.PUBLISHED)
            .filter(event -> event.getPublishedAt() != null && event.getPublishedAt().isBefore(beforeTime))
            .map(Outbox::getEventId)
            .collect(Collectors.toList());
        
        toDelete.forEach(storage::remove);
        
        log.info("Deleted {} published outbox events before {}", toDelete.size(), beforeTime);
        return toDelete.size();
    }
}