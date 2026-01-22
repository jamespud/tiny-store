package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.SeckillPriceRuleEntity;

@Repository
public interface JpaSeckillPriceRuleRepository extends JpaRepository<SeckillPriceRuleEntity, UUID> {

	@Query("SELECT r FROM SeckillPriceRuleEntity r WHERE r.status = 'ACTIVE' AND r.startTime <= :now AND r.endTime > :now ORDER BY r.version DESC")
	List<SeckillPriceRuleEntity> findActive(@Param("now") LocalDateTime now);
}

