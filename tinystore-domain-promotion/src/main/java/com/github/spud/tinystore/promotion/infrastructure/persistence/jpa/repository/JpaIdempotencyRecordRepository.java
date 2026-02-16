package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.IdempotencyRecordEntity;

@Repository
public interface JpaIdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {

	Optional<IdempotencyRecordEntity> findByOpAndIdempotencyKey(String op, String idempotencyKey);

	void deleteByExpiresAtBefore(LocalDateTime now);
}
