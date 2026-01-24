package com.github.spud.tinystore.product.infrastructure.persistence.jpa.mapper;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.Brand;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductAttribute;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductCategory;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductStatus;
import com.github.spud.tinystore.product.infrastructure.persistence.jpa.entity.ProductEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ProductMapper - Maps between Product domain model and ProductEntity
 * <p>
 * Mapping responsibilities: - Convert domain Product aggregate to ProductEntity for persistence -
 * Convert ProductEntity to domain Product aggregate for retrieval - Handle value object conversions
 * (ProductId, Status, etc.)
 */
@Component
public class ProductMapper {

	/**
	 * Convert domain Product to ProductEntity
	 *
	 * @param product  Domain product aggregate
	 * @param tenantId Tenant identifier
	 * @return ProductEntity for persistence
	 */
	public ProductEntity toEntity(Product product, String tenantId) {
		if (product == null) {
			return null;
		}

		ProductEntity entity = new ProductEntity();
		entity.setTenantId(tenantId);
		entity.setProductId(product.getProductId() != null ? product.getProductId().getId() : null);
		entity.setName(product.getName());
		entity.setStatus(
			product.getStatus() != null ? product.getStatus().name() : ProductStatus.DRAFT.name());
		entity.setCategoryId(
			product.getCategory() != null ? product.getCategory().getCategoryId() : null);

		return entity;
	}

	/**
	 * Convert ProductEntity to domain Product
	 *
	 * @param entity ProductEntity from database
	 * @return Domain product aggregate
	 */
	public Product toDomain(ProductEntity entity) {
		if (entity == null) {
			return null;
		}
		ProductId productId = ProductId.of(entity.getProductId());
		ProductCategory category = new ProductCategory();
		category.setCategoryId(entity.getCategoryId());
		Brand brand = new Brand("DEFAULT_BRAND");
		List<ProductAttribute> attributes = List.of(new ProductAttribute("placeholder-key", "placeholder-value"));
		ProductStatus status = entity.getStatus() != null ? ProductStatus.valueOf(entity.getStatus()) : ProductStatus.DRAFT;
		return Product.rehydrate(
			productId,
			entity.getName(),
			category,
			brand,
			Product.ProductType.PHYSICAL_GOODS,
			attributes,
			status,
			entity.getCreatedAt(),
			entity.getUpdatedAt()
		);
	}

	/**
	 * Update entity from domain product (for update operations)
	 *
	 * @param entity  Existing entity to update
	 * @param product Domain product with new values
	 */
	public void updateEntity(ProductEntity entity, Product product) {
		if (entity == null || product == null) {
			return;
		}

		entity.setName(product.getName());
		entity.setStatus(product.getStatus().name());
		entity.setCategoryId(
			product.getCategory() != null ? product.getCategory().getCategoryId() : null);
	}
}
