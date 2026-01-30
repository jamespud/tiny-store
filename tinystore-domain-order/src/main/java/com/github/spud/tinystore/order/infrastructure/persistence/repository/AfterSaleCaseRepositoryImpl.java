package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.github.spud.tinystore.order.domain.enums.AfterSaleStatus;
import com.github.spud.tinystore.order.domain.enums.AfterSaleType;
import com.github.spud.tinystore.order.domain.model.AfterSaleCase;
import com.github.spud.tinystore.order.domain.repository.AfterSaleCaseRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.AfterSaleCaseEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.AfterSaleCaseJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 售后案件仓储实现（JPA 适配器）
 * 负责 AfterSaleCaseEntity <-> AfterSaleCase 领域模型转换
 */
@Slf4j
@Repository
public class AfterSaleCaseRepositoryImpl implements AfterSaleCaseRepository {

    @Autowired
    private AfterSaleCaseJpaRepository afterSaleCaseJpaRepository;

    @Override
    public AfterSaleCase save(AfterSaleCase afterSaleCase) {
        AfterSaleCaseEntity entity = toEntity(afterSaleCase);
        entity = afterSaleCaseJpaRepository.save(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<AfterSaleCase> findByCaseId(String caseId) {
        return afterSaleCaseJpaRepository.findByCaseId(caseId)
            .map(this::toDomain);
    }

    @Override
    public Optional<AfterSaleCase> findByRefundId(String refundId) {
        return afterSaleCaseJpaRepository.findByRefundId(refundId)
            .map(this::toDomain);
    }

    @Override
    public List<AfterSaleCase> findByTradeId(String tradeId) {
        return afterSaleCaseJpaRepository.findByTradeId(tradeId).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<AfterSaleCase> findByOrderId(String orderId) {
        return afterSaleCaseJpaRepository.findByOrderId(orderId).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    // =========== Entity <-> Domain Model 转换 ===========

    private AfterSaleCaseEntity toEntity(AfterSaleCase afterSaleCase) {
        return AfterSaleCaseEntity.builder()
            .caseId(afterSaleCase.getCaseId())
            .tradeId(afterSaleCase.getTradeId())
            .orderId(afterSaleCase.getOrderId())
            .caseType(afterSaleCase.getCaseType() != null ? afterSaleCase.getCaseType().getCode() : null)
            .caseStatus(afterSaleCase.getCaseStatus() != null ? afterSaleCase.getCaseStatus().getCode() : null)
            .refundId(afterSaleCase.getRefundId())
            .refundAmountCents(afterSaleCase.getRefundAmountCents())
            .createdAt(afterSaleCase.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .finishedAt(afterSaleCase.getFinishedAt())
            .build();
    }

    private AfterSaleCase toDomain(AfterSaleCaseEntity entity) {
        return AfterSaleCase.builder()
            .caseId(entity.getCaseId())
            .tradeId(entity.getTradeId())
            .orderId(entity.getOrderId())
            .caseType(entity.getCaseType() != null ? AfterSaleType.valueOf(entity.getCaseType()) : null)
            .caseStatus(entity.getCaseStatus() != null ? AfterSaleStatus.valueOf(entity.getCaseStatus()) : null)
            .refundId(entity.getRefundId())
            .refundAmountCents(entity.getRefundAmountCents())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .finishedAt(entity.getFinishedAt())
            .build();
    }
}
