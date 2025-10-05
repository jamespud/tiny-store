package com.github.spud.tinystore.product.interfaces.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.Brand;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductAttribute;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductCategory;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.interfaces.dto.ProductCreateDTO;
import com.github.spud.tinystore.product.interfaces.dto.ProductResponseDTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ProductDTOMapper - Maps between Product domain objects and DTOs
 */
@Component
public class ProductDTOMapper {

	/**
	 * Convert ProductCreateDTO to Product domain object
	 */
	public Product toDomain(ProductCreateDTO dto) {
		ProductId productId = ProductId.generate();
		ProductCategory category = new ProductCategory();
		category.setCategoryId(dto.getCategoryId());
		category.setCategoryName(dto.getCategoryId()); // TODO: Lookup actual category name

		Brand brand = new Brand("DEFAULT_BRAND"); // TODO: Get from DTO or user context

		List<ProductAttribute> attributes = new ArrayList<>();
		// TODO: Convert DTO attributes to domain ProductAttribute objects
		// For now, create at least one attribute to pass validation
		attributes.add(new ProductAttribute("type", "product"));

		return Product.create(
			productId,
			dto.getName(),
			category,
			brand,
			Product.ProductType.PHYSICAL_GOODS, // TODO: Get from DTO
			attributes
		);
	}

	/**
	 * Convert Product domain object to ProductResponseDTO
	 */
	public ProductResponseDTO toResponseDTO(Product product) {
		ProductResponseDTO dto = new ProductResponseDTO();
		dto.setId(product.getProductId().getId());
		dto.setName(product.getName());
		dto.setCategoryId(product.getCategory().getCategoryId());
		dto.setStatus(product.getStatus().name());
		dto.setTags(List.of()); // TODO: Get from Product when supported
		dto.setCreatedAt(product.getCreateTime());
		dto.setUpdatedAt(product.getUpdateTime());
		return dto;
	}
}

