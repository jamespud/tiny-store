package com.github.spud.tinystore.inventory.domain.command;

import com.github.spud.tinystore.inventory.domain.value.OccupyPair;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Canonical inventory confirm command.
 * <p>
 * Triggered on payment success. Moves PRE_DEDUCTED reservations to CONFIRMED (terminal).
 * Idempotency key must be derived from paymentId to guarantee exactly-once confirmation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryConfirmCommand {

    private String idempotencyKey;
    private String paymentId;
    private String tradeId;
    private String orderId;
    private String traceId;
    private List<OccupyPair> occupyPairs;
}
