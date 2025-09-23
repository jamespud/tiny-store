package com.github.spud.tinystore.order.application.command;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Spud
 * @date 2025/9/3
 */
@Getter
@Builder
public class CreateOrderCommand {

    private final String userId;

    private List<PreviewOrderCommand.ProductItem> productItems;

    private Set<String> coupons;

    private final String addressId;

    private final String deviceId;

    private final String idempotentKey;

    public List<String> getProductIds() {
        return List.of();
    }

    public Map<String, Integer> getProductMap() {
        return Map.of();
    }

}
