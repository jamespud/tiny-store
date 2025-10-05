package com.github.spud.tinystore.product.application;

import com.github.spud.tinystore.product.application.factory.ProductAggregateFactory;
import com.github.spud.tinystore.product.application.factory.SkuAggregateFactory;
import com.github.spud.tinystore.product.domain.common.DomainEventPublisher;
import com.github.spud.tinystore.product.domain.event.ProductCreatedEvent;
import com.github.spud.tinystore.product.domain.model.aggregate.Product;
import com.github.spud.tinystore.product.domain.model.aggregate.Sku;
import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import com.github.spud.tinystore.product.domain.model.valueobject.SkuId;
import com.github.spud.tinystore.product.domain.model.value.Money;
import com.github.spud.tinystore.product.domain.repository.ProductRepository;
import com.github.spud.tinystore.product.domain.repository.SkuRepository;

public class ProductService {
    private final ProductRepository productRepo;
    private final SkuRepository skuRepo;
    private final DomainEventPublisher publisher;

    public ProductService(ProductRepository productRepo, SkuRepository skuRepo, DomainEventPublisher publisher) {
        this.productRepo = productRepo; this.skuRepo = skuRepo; this.publisher = publisher;
    }

    public Product createProduct(ProductId id, String name) {
        var p = ProductAggregateFactory.createMinimal(id, name);
        productRepo.save(p);
        publisher.publish(new ProductCreatedEvent(id, name));
        return p;
    }

    public Sku createSku(SkuId id, ProductId productId, Money basePrice) {
        var sku = SkuAggregateFactory.createWithBasePrice(id, productId, basePrice);
        return skuRepo.save(sku);
    }
}
