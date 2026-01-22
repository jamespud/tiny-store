package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.FullReductionCampaignEntity;

@Repository
public interface JpaFullReductionCampaignRepository extends JpaRepository<FullReductionCampaignEntity, UUID> {

	@Query("SELECT c FROM FullReductionCampaignEntity c WHERE c.status = 'ACTIVE' AND c.startTime <= :now AND c.endTime > :now")
	List<FullReductionCampaignEntity> findActive(@Param("now") LocalDateTime now);
}

