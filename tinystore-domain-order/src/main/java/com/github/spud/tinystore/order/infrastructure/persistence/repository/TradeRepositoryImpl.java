package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Trade 仓储实现 - JPA 适配器
 */
@Repository
@RequiredArgsConstructor
public class TradeRepositoryImpl implements TradeRepository {
    
    private final TradeJpaRepository jpaRepository;
    
    @Override
    public Trade save(Trade trade) {
        TradeEntity entity = toEntity(trade);
        TradeEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }
    
    @Override
    public Optional<Trade> findByTradeId(String tradeId) {
        return jpaRepository.findByTradeId(tradeId)
            .map(this::toDomain);
    }
    
    @Override
    public void delete(Trade trade) {
        jpaRepository.findByTradeId(trade.getTradeId())
            .ifPresent(jpaRepository::delete);
    }
    
    private TradeEntity toEntity(Trade trade) {
        return TradeEntity.builder()
            .id(trade.getId())
            .tradeId(trade.getTradeId())
            .buyerId(trade.getBuyerId())
            .buyerNick(trade.getBuyerNick())
            .payStatus(trade.getPayStatus().name())
            .totalAmountCents(trade.getTotalAmountCents())
            .discountAmountCents(trade.getDiscountAmountCents())
            .payableAmountCents(trade.getPayableAmountCents())
            .createdAt(trade.getCreatedAt())
            .updatedAt(trade.getUpdatedAt())
            .build();
    }
    
    private Trade toDomain(TradeEntity entity) {
        return Trade.builder()
            .id(entity.getId())
            .tradeId(entity.getTradeId())
            .buyerId(entity.getBuyerId())
            .buyerNick(entity.getBuyerNick())
            .payStatus(com.github.spud.tinystore.order.domain.enums.PayStatus.valueOf(entity.getPayStatus()))
            .totalAmountCents(entity.getTotalAmountCents())
            .discountAmountCents(entity.getDiscountAmountCents())
            .payableAmountCents(entity.getPayableAmountCents())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
