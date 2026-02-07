package com.github.spud.tinystore.order.infrastructure.persistence.repository;

import com.github.spud.tinystore.order.domain.model.FulfillmentPackage;
import com.github.spud.tinystore.order.domain.repository.FulfillmentPackageRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity.FulfillmentPackageEntity;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.FulfillmentPackageJpaRepository;
import com.github.spud.tinystore.order.infrastructure.persistence.jpa.repository.PackageOrderRefJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 履约包裹仓储实现（JPA 适配器）
 * 负责 FulfillmentPackageEntity <-> FulfillmentPackage 领域模型转换
 */
@Slf4j
@Repository
public class FulfillmentPackageRepositoryImpl implements FulfillmentPackageRepository {

    @Autowired
    private FulfillmentPackageJpaRepository fulfillmentPackageJpaRepository;

    @Autowired
    private PackageOrderRefJpaRepository packageOrderRefJpaRepository;

    @Override
    public FulfillmentPackage save(FulfillmentPackage fulfillmentPackage) {
        // 查找现有entity（通过 packageId）
        FulfillmentPackageEntity entity = fulfillmentPackageJpaRepository
            .findByPackageId(fulfillmentPackage.getPackageId())
            .orElse(null);
        
        if (entity == null) {
            // 新建entity
            entity = toEntity(fulfillmentPackage);
        } else {
            // 更新现有entity的字段
            updateEntity(entity, fulfillmentPackage);
        }
        
        entity = fulfillmentPackageJpaRepository.save(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<FulfillmentPackage> findByPackageId(String packageId) {
        return fulfillmentPackageJpaRepository.findByPackageId(packageId)
            .map(this::toDomain);
    }

    @Override
    public List<FulfillmentPackage> findByTradeId(String tradeId) {
        return fulfillmentPackageJpaRepository.findByTradeId(tradeId).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<FulfillmentPackage> findByOrderId(String orderId) {
        // 通过包裹-订单关联表查询该订单的所有包裹ID
        List<String> packageIds = packageOrderRefJpaRepository.findByOrderId(orderId).stream()
            .map(ref -> ref.getPackageId())
            .collect(Collectors.toList());
        
        // 批量查询包裹实体（使用 packageId 而非主键 ID）
        if (packageIds.isEmpty()) {
            return new ArrayList<>();
        }
        return packageIds.stream()
            .map(packageId -> fulfillmentPackageJpaRepository.findByPackageId(packageId))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    // =========== Entity <-> Domain Model 转换 ===========

    private void updateEntity(FulfillmentPackageEntity entity, FulfillmentPackage fulfillmentPackage) {
        String logisticsStatus = fulfillmentPackage.getPackageStatus() != null 
            ? fulfillmentPackage.getPackageStatus().getCode() 
            : "CREATED";
        
        entity.setPackageId(fulfillmentPackage.getPackageId());
        entity.setTradeId(fulfillmentPackage.getTradeId());
        entity.setLogisticsNo(fulfillmentPackage.getWaybillNo());
        entity.setLogisticsCompanyId(fulfillmentPackage.getLogisticsCompany());
        entity.setLogisticsStatus(logisticsStatus);
        entity.setShippedAt(fulfillmentPackage.getShippedAt());
        entity.setDeliveredAt(fulfillmentPackage.getDeliveredAt());
        entity.setUpdatedAt(fulfillmentPackage.getUpdatedAt() != null ? fulfillmentPackage.getUpdatedAt() : LocalDateTime.now());
    }

    private FulfillmentPackageEntity toEntity(FulfillmentPackage fulfillmentPackage) {
        String logisticsStatus = fulfillmentPackage.getPackageStatus() != null 
            ? fulfillmentPackage.getPackageStatus().getCode() 
            : "CREATED";
        
        return FulfillmentPackageEntity.builder()
            .packageId(fulfillmentPackage.getPackageId())
            .tradeId(fulfillmentPackage.getTradeId())
            .sellerId("") // TODO: 需要从业务上下文传入 sellerId
            .logisticsNo(fulfillmentPackage.getWaybillNo())
            .logisticsCompanyId(fulfillmentPackage.getLogisticsCompany())
            .logisticsStatus(logisticsStatus)
            .shippedAt(fulfillmentPackage.getShippedAt())
            .deliveredAt(fulfillmentPackage.getDeliveredAt())
            .createdAt(fulfillmentPackage.getCreatedAt())
            .build();
    }

    private FulfillmentPackage toDomain(FulfillmentPackageEntity entity) {
        com.github.spud.tinystore.order.domain.enums.PackageStatus packageStatus = null;
        if (entity.getLogisticsStatus() != null) {
            try {
                packageStatus = com.github.spud.tinystore.order.domain.enums.PackageStatus.getByCode(entity.getLogisticsStatus());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown PackageStatus code: {}, defaulting to CREATED", entity.getLogisticsStatus());
                packageStatus = com.github.spud.tinystore.order.domain.enums.PackageStatus.CREATED;
            }
        }
        
        return FulfillmentPackage.builder()
            .id(entity.getId())
            .packageId(entity.getPackageId())
            .tradeId(entity.getTradeId())
            .packageStatus(packageStatus)
            .waybillNo(entity.getLogisticsNo())
            .logisticsCompany(entity.getLogisticsCompanyId())
            .shippedAt(entity.getShippedAt())
            .deliveredAt(entity.getDeliveredAt())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
