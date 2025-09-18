package com.github.spud.tinystore.product.domain.event;

import com.github.spud.tinystore.product.domain.model.valueobject.ProductId;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ProductCreatedEvent {
    private final ProductId productId;
    private final String productName;
    private final LocalDateTime createTime;

    public ProductCreatedEvent(ProductId productId, String productName) {
        this.productId = productId;
        this.productName = productName;
        this.createTime = LocalDateTime.now();
    }
}