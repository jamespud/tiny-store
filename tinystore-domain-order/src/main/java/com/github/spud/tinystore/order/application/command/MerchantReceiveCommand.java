package com.github.spud.tinystore.order.application.command;

import lombok.Builder;
import lombok.Data;

/**
 * Command for merchant to receive order
 */
@Data
@Builder
public class MerchantReceiveCommand {
    private String orderId;
    private String merchantId;
}