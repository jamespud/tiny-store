package com.github.spud.tinystore.inventory.application.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryDeductCommand;
import com.github.spud.tinystore.inventory.domain.command.InventoryReleaseCommand;
import com.github.spud.tinystore.inventory.domain.service.InventoryDeductDomainService;
import com.github.spud.tinystore.inventory.domain.value.DeductResult;
import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.DeductResponse;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryReleaseResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存扣减应用服务（薄层编排）
 * <p>
 * 职责：DTO ↔ Command 转换，调用领域服务，Domain Result → Response DTO。
 */
@Service
public class InventoryDeductAppService {

    private final InventoryDeductDomainService domainService;

    public InventoryDeductAppService(InventoryDeductDomainService domainService) {
        this.domainService = domainService;
    }

    public DeductResponse deduct(String idempotencyKey, DeductRequest request) {
        InventoryDeductCommand command = InventoryDeductCommand.builder()
                .orderId(request.getOrderId())
                .idempotencyKey(idempotencyKey)
                .items(request.getItems().stream()
                        .map(item -> InventoryDeductCommand.Item.builder()
                                .shopId(item.getShopId())
                                .skuId(item.getSkuId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build();

        DeductResult result = domainService.deduct(command);
        return toDeductResponse(result);
    }

    public InventoryReleaseResponse release(String idempotencyKey, InventoryReleaseRequest request) {
        InventoryReleaseCommand command = InventoryReleaseCommand.builder()
                .orderId(request.getOrderId())
                .idempotencyKey(idempotencyKey)
                .reason(request.getReason())
                .occupyPairs(request.getOccupyPairs().stream()
                        .map(p -> OccupyPair.builder()
                                .shopId(p.getShopId())
                                .skuId(p.getSkuId())
                                .occupyId(p.getOccupyId())
                                .build())
                        .toList())
                .build();

        DeductResult result = domainService.release(command);
        if (result.isSuccess()) {
            return InventoryReleaseResponse.ok(result.getMessage());
        }
        return InventoryReleaseResponse.fail(result.getMessage());
    }

    // ========================== 转换 ==========================

    private DeductResponse toDeductResponse(DeductResult result) {
        if (result.isSuccess()) {
            List<DeductResponse.OccupyPairDto> pairs = result.getOccupyPairs() == null
                    ? List.of()
                    : result.getOccupyPairs().stream()
                    .map(p -> DeductResponse.OccupyPairDto.builder()
                            .shopId(p.getShopId())
                            .skuId(p.getSkuId())
                            .occupyId(p.getOccupyId())
                            .build())
                    .toList();
            return DeductResponse.ok(pairs);
        }
        return DeductResponse.fail(result.getLackSkuIds(), result.getMessage());
    }
}
