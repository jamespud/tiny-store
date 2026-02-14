package com.github.spud.tinystore.inventory.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class InventoryReleaseRequest {

    @NotBlank(message = "orderId 不能为空")
    private String orderId;

    private String reason;

    @NotEmpty(message = "回滚项不能为空")
    @Valid
    private List<OccupyPairDto> occupyPairs;

    @Data
    public static class OccupyPairDto {
        @NotBlank(message = "shopId 不能为空")
        private String shopId;

        @NotBlank(message = "skuId 不能为空")
        private String skuId;

        @NotBlank(message = "occupyId 不能为空")
        private String occupyId;
    }
}
