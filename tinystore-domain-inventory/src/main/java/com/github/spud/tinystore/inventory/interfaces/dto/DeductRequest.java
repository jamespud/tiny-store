package com.github.spud.tinystore.inventory.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class DeductRequest {

    @NotBlank(message = "orderId 不能为空")
    private String orderId;

    @NotEmpty(message = "扣减项不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank(message = "shopId 不能为空")
        private String shopId;

        @NotBlank(message = "skuId 不能为空")
        private String skuId;

        @NotNull(message = "quantity 不能为空")
        @Positive(message = "quantity 必须大于 0")
        private Integer quantity;
    }
}
