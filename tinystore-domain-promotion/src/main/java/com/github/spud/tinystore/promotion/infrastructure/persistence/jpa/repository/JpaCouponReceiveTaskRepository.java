package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.CouponReceiveTaskEntity;

@Repository
public interface JpaCouponReceiveTaskRepository extends JpaRepository<CouponReceiveTaskEntity, UUID> {

	Optional<CouponReceiveTaskEntity> findByIdempotencyKey(String idempotencyKey);

	List<CouponReceiveTaskEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);

	@Modifying
	@Query("UPDATE CouponReceiveTaskEntity t SET t.status = :to, t.updatedAt = :now WHERE t.id = :id AND t.status = :from")
	int updateStatus(@Param("id") UUID id, @Param("from") String from, @Param("to") String to,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE CouponReceiveTaskEntity t SET t.status = :status, t.lastError = :lastError, t.updatedAt = :now WHERE t.id = :id")
	int updateStatusAndError(@Param("id") UUID id, @Param("status") String status, @Param("lastError") String lastError,
		@Param("now") LocalDateTime now);
}

