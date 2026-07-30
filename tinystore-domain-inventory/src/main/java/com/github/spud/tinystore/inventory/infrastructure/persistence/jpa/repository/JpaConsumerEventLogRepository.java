package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.ConsumerEventLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface JpaConsumerEventLogRepository extends JpaRepository<ConsumerEventLogEntity, UUID> {
    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
