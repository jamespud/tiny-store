package com.github.spud.tinystore.product.infrastructure.persistence.jpa.adapter;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.domain.repository.SkuRepository;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.SkuEntity;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper.SkuMapper;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository.JpaSkuRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SkuRepositoryAdapter - Adapts JPA repository to domain SKU repository
 */
@Repository
public class SkuRepositoryAdapter implements SkuRepository {
    
    private final JpaSkuRepository jpaRepository;
    private final SkuMapper mapper;
    private final TenantRepositoryConfig.TenantContext tenantContext;
    
    public SkuRepositoryAdapter(
            JpaSkuRepository jpaRepository,
            SkuMapper mapper,
            TenantRepositoryConfig.TenantContext tenantContext) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }
    
    @Override
    public Sku save(Sku sku) {
        String tenantId = tenantContext.getTenantId();
        SkuEntity entity = mapper.toEntity(sku, tenantId);
        SkuEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
    
    @Override
    public Optional<Sku> findById(SkuId skuId) {
        // TODO: Convert SkuId to entity ID and implement lookup
        return Optional.empty();
    }
    
    @Override
    public List<Sku> findByProductId(ProductId productId) {
        String tenantId = tenantContext.getTenantId();
        // TODO: Convert ProductId to string and implement lookup
        List<SkuEntity> entities = jpaRepository.findByTenantIdAndProductId(tenantId, productId.toString());
        return entities.stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public void delete(SkuId skuId) {
        // TODO: Implement delete logic
        throw new UnsupportedOperationException("Delete not yet implemented");
    }
}

