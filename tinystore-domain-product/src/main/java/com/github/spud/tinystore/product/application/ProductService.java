package com.github.spud.tinystore.product.application;

import com.github.spud.tinystore.product.domain.common.DomainEventPublisher;
import com.github.spud.tinystore.product.domain.event.ProductCreated;
import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.id.ProductId;
import com.github.spud.tinystore.product.domain.model.id.SkuId;
import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.repo.ProductRepository;
import com.github.spud.tinystore.product.domain.repo.SkuRepository;

public class ProductService {
    private final ProductRepository productRepo;
    private final SkuRepository skuRepo;
    private final DomainEventPublisher publisher;

    public ProductService(ProductRepository productRepo, SkuRepository skuRepo, DomainEventPublisher publisher) {
        this.productRepo = productRepo; this.skuRepo = skuRepo; this.publisher = publisher;
    }

    public Product createProduct(ProductId id, String name) {
        var p = Product.create(id, name);
        productRepo.save(p);
        publisher.publish(new ProductCreated(id, name));
        return p;
    }

    public Sku createSku(SkuId id, ProductId productId, Money basePrice) {
        var s = Sku.create(id, productId, basePrice);
        return skuRepo.save(s);
    }
}
