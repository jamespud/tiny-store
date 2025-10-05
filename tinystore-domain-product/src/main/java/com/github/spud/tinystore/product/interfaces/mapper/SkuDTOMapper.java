package com.github.spud.tinystore.product.interfaces.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuAttributePack;
import com.github.spud.tinystore.product.domain.model.valueobject.SpecificationCombination;
import com.github.spud.tinystore.product.interfaces.dto.SkuCreateDTO;
import com.github.spud.tinystore.product.interfaces.dto.SkuResponseDTO;
import com.github.spud.tinystore.product.interfaces.dto.SkuUpdateDTO;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * SkuDTOMapper - Maps between Sku domain objects and DTOs
 */
@Component
public class SkuDTOMapper {

	/**
	 * Convert SkuCreateDTO to Sku domain object
	 */
	public Sku toDomain(SkuCreateDTO dto, String productId) {
		String skuId = UUID.randomUUID().toString().replace("-", "");

		// Parse specification combination from string
		SpecificationCombination specs = new SpecificationCombination(dto.getSpecCombination());

		// Create SKU using domain factory
		Sku sku = Sku.create(skuId, productId, specs, dto.getBarCode());

		// Set attributes if present
		if (dto.getAttributes() != null && !dto.getAttributes().isEmpty()) {
			SkuAttributePack attributePack = new SkuAttributePack(dto.getAttributes());
			sku.setAttributes(attributePack);
		}

		// TODO: Set price/stock if supported by domain

		return sku;
	}

	/**
	 * Convert Sku domain object to SkuResponseDTO
	 */
	public SkuResponseDTO toResponseDTO(Sku sku) {
		SkuResponseDTO dto = new SkuResponseDTO();
		dto.setId(sku.getSkuId());
		dto.setProductId(sku.getProductId());
		dto.setSpecCombination(sku.getSpecs().toString());
		dto.setBarCode(sku.getBarCode());
		dto.setStatus(sku.getStatus().name());
		dto.setCreatedAt(sku.getCreateTime());

		// Set attributes if present
		if (sku.getAttributes() != null) {
			dto.setAttributes(sku.getAttributes().toMap());
		}

		// TODO: Set price, stock when supported
		return dto;
	}

	/**
	 * Apply SkuUpdateDTO to existing Sku
	 */
	public void applyUpdate(Sku sku, SkuUpdateDTO dto) {
		if (dto.getBarCode() != null) {
			sku.setBarCode(dto.getBarCode());
		}

		if (dto.getAttributes() != null) {
			SkuAttributePack attributePack = new SkuAttributePack(dto.getAttributes());
			sku.setAttributes(attributePack);
		}

		// TODO: Update price/stock if supported
	}
}

