package com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.model.valueobject.SpecificationCombination;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuStatus;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.SkuEntity;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * SkuMapper - Maps between Sku domain model and SkuEntity
 * <p>
 * Mapping responsibilities: - Convert domain Sku aggregate to SkuEntity for persistence - Convert
 * SkuEntity to domain Sku aggregate for retrieval - Handle value object conversions (SkuId,
 * SpecificationCombination, etc.) - Normalize spec combination for uniqueness constraint
 */
@Component
public class SkuMapper {

	/**
	 * Convert domain Sku to SkuEntity
	 *
	 * @param sku      Domain sku aggregate
	 * @param tenantId Tenant identifier
	 * @return SkuEntity for persistence
	 */
	public SkuEntity toEntity(Sku sku, String tenantId) {
		if (sku == null) {
			return null;
		}

		SkuEntity entity = new SkuEntity();
		entity.setTenantId(tenantId);
		entity.setProductId(sku.getProductId());
		entity.setSkuId(sku.getSkuId());
		entity.setBarCode(sku.getBarCode());
		entity.setStatus(sku.getStatus() != null ? sku.getStatus().name() : SkuStatus.AVAILABLE.name());

		// Normalize spec combination for uniqueness (simplified)
		if (sku.getSpecs() != null) {
			entity.setSpecCombination(sku.getSpecs().toString());
		}

		return entity;
	}

	/**
	 * Convert SkuEntity to domain Sku
	 *
	 * @param entity SkuEntity from database
	 * @return Domain sku aggregate
	 */
	public Sku toDomain(SkuEntity entity) {
		if (entity == null) {
			return null;
		}
		SpecificationCombination specs = new SpecificationCombination(entity.getSpecCombination());
		Sku sku = Sku.create(
			entity.getSkuId(),
			entity.getProductId(),
			specs,
			entity.getBarCode()
		);
		if (entity.getStatus() != null) {
			sku.setStatus(SkuStatus.valueOf(entity.getStatus()));
		}
		if (entity.getUnitPriceCents() != null) {
			sku.setBasePrice(Money.of(BigDecimal.valueOf(entity.getUnitPriceCents(), 2)));
		}
		sku.setCreateTime(entity.getCreatedAt());
		return sku;
	}
}

