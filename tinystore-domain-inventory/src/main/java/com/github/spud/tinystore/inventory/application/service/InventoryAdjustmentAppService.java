package com.github.spud.tinystore.inventory.application.service;

import com.github.spud.tinystore.inventory.domain.command.InventoryAdjustCommand;
import com.github.spud.tinystore.inventory.domain.enums.AdjustmentReason;
import com.github.spud.tinystore.inventory.domain.service.InventoryAdjustmentDomainService;
import com.github.spud.tinystore.inventory.domain.value.AdjustmentResult;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryAdjustRequest;
import com.github.spud.tinystore.inventory.interfaces.dto.InventoryAdjustResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InventoryAdjustmentAppService {

    private final InventoryAdjustmentDomainService domainService;

    public InventoryAdjustmentAppService(InventoryAdjustmentDomainService domainService) {
        this.domainService = domainService;
    }

    public InventoryAdjustResponse adjust(String idempotencyKey, InventoryAdjustRequest request) {
        MDC.put("orderId", request.getTradeId());
        try {
            InventoryAdjustCommand command = InventoryAdjustCommand.builder()
                    .idempotencyKey(idempotencyKey)
                    .reason(AdjustmentReason.fromCode(request.getReason()))
                    .referenceId(request.getReferenceId())
                    .tradeId(request.getTradeId())
                    .items(request.getItems().stream()
                            .map(i -> InventoryAdjustCommand.Item.builder()
                                    .shopId(i.getShopId()).skuId(i.getSkuId()).delta(i.getDelta()).build())
                            .toList())
                    .build();

            AdjustmentResult result;
            try {
                result = domainService.adjust(command);
            } catch (IllegalStateException e) {
                // e.g. negative delta would make total < 0
                log.warn("Adjustment rejected: {}", e.getMessage());
                return InventoryAdjustResponse.fail(e.getMessage());
            }

            if (!result.isSuccess()) {
                return InventoryAdjustResponse.fail(result.getMessage());
            }

            // Redis best-effort AFTER the DB tx committed (adjust() is @Transactional; on return tx is committed).
            domainService.compensateRedisAfterCommit(result);
            return InventoryAdjustResponse.ok();
        } finally {
            MDC.remove("orderId");
        }
    }
}
