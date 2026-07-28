package com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.repository;

import com.github.spud.tinystore.inventory.infrastructure.persistence.jpa.entity.InventoryReconcileLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaInventoryReconcileLogRepository extends JpaRepository<InventoryReconcileLogEntity, Long> {
}
