package com.github.spud.tinystore.product.domain.model.valueobject;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.UUID;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkuId {
    String id;

    // 生成唯一ID（封装ID生成策略）
    public static SkuId generate() {
        return new SkuId(UUID.randomUUID().toString().replace("-", ""));
    }

    // 从字符串恢复（用于查询）
    public static SkuId of(String id) {
        return new SkuId(id);
    }
}
