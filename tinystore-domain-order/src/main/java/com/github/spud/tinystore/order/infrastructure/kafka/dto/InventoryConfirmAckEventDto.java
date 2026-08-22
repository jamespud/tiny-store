package com.github.spud.tinystore.order.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Inventory domain confirm ack event. Published by the inventory domain after a
 * reservation confirm (\u005bINVENTORY_CONFIRMED\u005d) or confirm conflict
 * (\u005bINVENTORY_CONFIRM_CONFLICT\u005d).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class InventoryConfirmAckEventDto {
    private String eventId;
    private String eventType;
    private String tradeId;
    private String orderId;
    private String status;
    private String reason;
}
