package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.ConsumerEventLogEntity;

@Repository
public interface JpaConsumerEventLogRepository extends JpaRepository<ConsumerEventLogEntity, UUID> {

	boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
