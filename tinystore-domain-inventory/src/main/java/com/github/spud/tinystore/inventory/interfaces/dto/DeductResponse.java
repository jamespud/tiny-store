package com.github.spud.tinystore.inventory.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeductResponse {

    private boolean success;
    private String message;
    @Builder.Default
    private List<OccupyPairDto> occupyPairs = Collections.emptyList();
    @Builder.Default
    private List<String> lackSkuIds = Collections.emptyList();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OccupyPairDto {
        private String shopId;
        private String skuId;
        private String occupyId;
    }

    public static DeductResponse ok(List<OccupyPairDto> occupyPairs) {
        return DeductResponse.builder()
                .success(true)
                .message("ok")
                .occupyPairs(occupyPairs == null ? Collections.emptyList() : occupyPairs)
                .build();
    }

    public static DeductResponse fail(List<String> lackSkuIds, String message) {
        return DeductResponse.builder()
                .success(false)
                .message(message)
                .lackSkuIds(lackSkuIds == null ? Collections.emptyList() : lackSkuIds)
                .build();
    }
}
