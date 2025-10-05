package com.github.spud.tinystore.product.application.factory;

import java.util.List;

import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.id.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.Brand;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductAttribute;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductCategory;

public final class ProductAggregateFactory {

    private ProductAggregateFactory() {
    }

    public static Product createMinimal(ProductId id, String name) {
        com.github.spud.tinystore.product.domain.model.valueobject.ProductId aggregateId =
                com.github.spud.tinystore.product.domain.model.valueobject.ProductId.of(id.value());
        ProductCategory category = new ProductCategory();
        Brand brand = new Brand();
        List<ProductAttribute> attributes = List.of(new ProductAttribute("placeholder-key", "placeholder-value"));
        return Product.create(aggregateId, name, category, brand, Product.ProductType.PHYSICAL_GOODS, attributes);
    }
}
