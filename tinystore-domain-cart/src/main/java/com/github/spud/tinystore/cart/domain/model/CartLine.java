package com.github.spud.tinystore.cart.domain.model;

import java.time.LocalDateTime;

public record CartLine(Product product, Integer quantity, LocalDateTime addedAt, Boolean selected,
                       Boolean valid) {

}

