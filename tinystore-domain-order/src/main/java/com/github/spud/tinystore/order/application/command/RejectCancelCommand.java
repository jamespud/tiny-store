package com.github.spud.tinystore.order.application.command;

import lombok.Builder;
import lombok.Data;

/**
 * Command to reject cancellation
 */
@Data
@Builder
public class RejectCancelCommand {
    private String orderId;
    private String approver;
    private String reason;
}