package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.domain.model.Trade;
import com.github.spud.tinystore.order.domain.repository.TradeRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.TradeEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.TradeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Trade 仓储实现 - JPA 适配器
 */
@Repository
@RequiredArgsConstructor
public class TradeRepositoryImpl implements TradeRepository {
    
    private final TradeJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;
    
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

    @Override
    public List<String> findStalePendingCommit(LocalDateTime threshold) {
        return jpaRepository.findStalePendingCommitTradeIds(threshold);
    }

    @Override
    public List<String> findPaidTradeIdsSince(LocalDateTime before) {
        return jpaRepository.findPaidTradeIdsSince(before);
    }
    
    private TradeEntity toEntity(Trade trade) {
        return TradeEntity.builder()
            .id(trade.getId())
            .version(trade.getVersion())
            .tradeId(trade.getTradeId())
            .buyerId(trade.getBuyerId())
            .buyerNick(trade.getBuyerNick())
            .payStatus(trade.getPayStatus().name())
            .totalAmountCents(trade.getTotalAmountCents())
            .discountAmountCents(trade.getDiscountAmountCents())
            .payableAmountCents(trade.getPayableAmountCents())
            .promotionQuoteId(trade.getPromotionQuoteId())
            .promotionInputHash(trade.getPromotionInputHash())
            .inventoryReservationId(trade.getInventoryReservationId())
            .promotionCommitStatus(trade.getPromotionCommitStatus() != null
                    ? trade.getPromotionCommitStatus() : "PENDING")
            .couponCodes(serializeCouponCodes(trade.getCouponCodes()))
            .createdAt(trade.getCreatedAt())
            .updatedAt(trade.getUpdatedAt())
            .closedAt(trade.getClosedAt())
            .build();
    }
    
    private Trade toDomain(TradeEntity entity) {
        return Trade.builder()
            .id(entity.getId())
            .version(entity.getVersion())
            .tradeId(entity.getTradeId())
            .buyerId(entity.getBuyerId())
            .buyerNick(entity.getBuyerNick())
            .payStatus(com.github.spud.tinystore.order.domain.enums.PayStatus.valueOf(entity.getPayStatus()))
            .totalAmountCents(entity.getTotalAmountCents())
            .discountAmountCents(entity.getDiscountAmountCents())
            .payableAmountCents(entity.getPayableAmountCents())
            .promotionQuoteId(entity.getPromotionQuoteId())
            .promotionInputHash(entity.getPromotionInputHash())
            .inventoryReservationId(entity.getInventoryReservationId())
            .promotionCommitStatus(entity.getPromotionCommitStatus())
            .couponCodes(deserializeCouponCodes(entity.getCouponCodes()))
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .closedAt(entity.getClosedAt())
            .build();
    }
    
    private String serializeCouponCodes(List<String> couponCodes) {
        if (couponCodes == null || couponCodes.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(couponCodes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize couponCodes", e);
        }
    }
    
    private List<String> deserializeCouponCodes(String couponCodesJson) {
        if (couponCodesJson == null || couponCodesJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(couponCodesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
