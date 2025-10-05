package com.github.spud.tinystore.product.infrastructure.persistence.jpa.adapter;

import com.github.spud.tinystore.product.domain.repository.PricingRuleRepository;
import com.github.spud.tinystore.product.domain.rules.PricingRule;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.config.TenantRepositoryConfig;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper.PricingRuleMapper;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository.JpaPricingRuleRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PricingRuleRepositoryAdapter - Adapts JPA repository for pricing rules
 */
@Repository
public class PricingRuleRepositoryAdapter implements PricingRuleRepository {
    
    private final JpaPricingRuleRepository jpaRepository;
    private final PricingRuleMapper mapper;
    private final TenantRepositoryConfig.TenantContext tenantContext;
    
    public PricingRuleRepositoryAdapter(
            JpaPricingRuleRepository jpaRepository,
            PricingRuleMapper mapper,
            TenantRepositoryConfig.TenantContext tenantContext) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }
    
    @Override
    public PricingRule save(PricingRule rule) {
        String tenantId = tenantContext.getTenantId();
        // TODO: Implement mapping and save
        throw new UnsupportedOperationException("Save not yet fully implemented");
    }
    
    @Override
    public Optional<PricingRule> findByRuleCode(String tenantId, String ruleCode) {
        // TODO: Implement find by rule code
        return Optional.empty();
    }
    
    @Override
    public List<PricingRule> findActiveRulesByProduct(String tenantId, String productId, Instant currentTime) {
        LocalDateTime time = LocalDateTime.ofInstant(currentTime, ZoneId.systemDefault());
        return jpaRepository.findActiveRulesByProduct(tenantId, productId, time).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<PricingRule> findActiveRulesByCategory(String tenantId, String categoryId, Instant currentTime) {
        LocalDateTime time = LocalDateTime.ofInstant(currentTime, ZoneId.systemDefault());
        return jpaRepository.findActiveRulesByCategory(tenantId, categoryId, time).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<PricingRule> findActiveRulesByTags(String tenantId, List<String> userTags, Instant currentTime) {
        // TODO: Implement tag-based filtering
        return List.of();
    }
    
    @Override
    public List<PricingRule> findAllActiveRules(String tenantId, Instant currentTime) {
        LocalDateTime time = LocalDateTime.ofInstant(currentTime, ZoneId.systemDefault());
        return jpaRepository.findAllActiveRules(tenantId, time).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
