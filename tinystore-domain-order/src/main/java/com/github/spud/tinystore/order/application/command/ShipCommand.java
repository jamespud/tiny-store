package com.github.spud.tinystore.order.application.command;

import lombok.Builder;
import lombok.Data;

/**
 * Command to ship order
 */
@Data
@Builder
public class ShipCommand {
    private String orderId;
    private String subOrderId;
    private String shipmentInfo;
    private String merchantId;
}