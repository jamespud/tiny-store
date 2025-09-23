package com.github.spud.tinystore.order.application.command;

import lombok.Builder;
import lombok.Data;

/**
 * Command to approve cancellation
 */
@Data
@Builder
public class ApproveCancelCommand {
    private String orderId;
    private String approver;
}