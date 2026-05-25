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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存扣减应用服务（Compatibility Façade）
 * <p>
 * 当 inventory.reservation.canonical-enabled=true 时，此服务委派给 InventoryReservationAppService。
 * 当 flag=false 时，维持原有 InventoryDeductDomainService 行为（legacy V2 deduct 链路）。
 * <p>
 * 对外 contract 不变：/api/inventory/deduct 和 /api/inventory/release
 *
 * @deprecated 新代码应直接使用 InventoryReservationAppService；本类仅用于兼容过渡。
 */
@Slf4j
@Service
public class InventoryDeductAppService {

    private final InventoryDeductDomainService legacyDomainService;
    private final InventoryReservationAppService canonicalService;

    @Value("${inventory.reservation.canonical-enabled:false}")
    private boolean canonicalEnabled;

    public InventoryDeductAppService(InventoryDeductDomainService legacyDomainService,
                                     InventoryReservationAppService canonicalService) {
        this.legacyDomainService = legacyDomainService;
        this.canonicalService = canonicalService;
    }

    public DeductResponse deduct(String idempotencyKey, DeductRequest request) {
        if (canonicalEnabled) {
            log.debug("deduct: routing to canonical reserve (canonical-enabled=true)");
            return canonicalService.reserve(idempotencyKey, request);
        }
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
        DeductResult result = legacyDomainService.deduct(command);
        return toDeductResponse(result);
    }

    public InventoryReleaseResponse release(String idempotencyKey, InventoryReleaseRequest request) {
        if (canonicalEnabled) {
            log.debug("release: routing to canonical release (canonical-enabled=true)");
            return canonicalService.release(idempotencyKey, request);
        }
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
        DeductResult result = legacyDomainService.release(command);
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
