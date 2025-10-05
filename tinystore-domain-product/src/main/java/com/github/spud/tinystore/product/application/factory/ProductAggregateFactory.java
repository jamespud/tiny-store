package com.github.spud.tinystore.product.application.factory;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.valueobject.Brand;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductAttribute;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductCategory;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import java.util.List;

public final class ProductAggregateFactory {

	private ProductAggregateFactory() {
	}

	public static Product createMinimal(ProductId id, String name) {
		ProductCategory category = new ProductCategory();
		Brand brand = new Brand();
		List<ProductAttribute> attributes = List.of(
			new ProductAttribute("placeholder-key", "placeholder-value"));
		return Product.create(id, name, category, brand, Product.ProductType.PHYSICAL_GOODS,
			attributes);
	}
}
