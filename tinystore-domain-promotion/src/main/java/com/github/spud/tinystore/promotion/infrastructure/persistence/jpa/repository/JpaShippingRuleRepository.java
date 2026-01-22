package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.ShippingRuleEntity;

@Repository
public interface JpaShippingRuleRepository extends JpaRepository<ShippingRuleEntity, UUID> {

	List<ShippingRuleEntity> findByStatusOrderByVersionDesc(String status);
}
