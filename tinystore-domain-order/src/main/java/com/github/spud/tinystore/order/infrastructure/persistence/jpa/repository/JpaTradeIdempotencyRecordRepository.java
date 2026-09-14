package com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeIdempotencyRecordEntity;

public interface JpaTradeIdempotencyRecordRepository
    extends JpaRepository<TradeIdempotencyRecordEntity, String> {
}
