package com.github.spud.tinystore.product.domain.model.valueobject;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.UUID;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductId {
    String id;

    // 生成唯一ID（封装ID生成策略）
    public static ProductId generate() {
        return new ProductId(UUID.randomUUID().toString().replace("-", ""));
    }

    // 从字符串恢复（用于查询）
    public static ProductId of(String id) {
        return new ProductId(id);
    }
}
