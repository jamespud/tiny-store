package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.ConsumerEventLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaConsumerEventLogRepository extends JpaRepository<ConsumerEventLogEntity, UUID> {
    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
