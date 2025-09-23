package com.github.spud.tinystore.order.domain.exception;

import com.github.spud.tinystore.order.domain.status.CoreFlowStatus;

/**
 * Exception thrown when an invalid state transition is attempted
 * 
 * @author Spud
 * @date 2025/9/22
 */
public class OrderTransitionNotAllowedException extends OrderDomainException {

    public enum ReasonCode {
        ILLEGAL_STATE,
        TERMINAL_STATE,
        MISSING_PREREQUISITE,
        WINDOW_CLOSED,
        INVALID_TRANSITION
    }

    private final CoreFlowStatus fromStatus;
    private final CoreFlowStatus toStatus;
    private final ReasonCode reasonCode;

    public OrderTransitionNotAllowedException(CoreFlowStatus fromStatus, CoreFlowStatus toStatus, ReasonCode reasonCode) {
        super(String.format("Invalid transition from %s to %s: %s", fromStatus, toStatus, reasonCode));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reasonCode = reasonCode;
    }

    public OrderTransitionNotAllowedException(CoreFlowStatus fromStatus, String operation, ReasonCode reasonCode) {
        super(String.format("Cannot %s in state %s: %s", operation, fromStatus, reasonCode));
        this.fromStatus = fromStatus;
        this.toStatus = null;
        this.reasonCode = reasonCode;
    }

    public CoreFlowStatus getFromStatus() {
        return fromStatus;
    }

    public CoreFlowStatus getToStatus() {
        return toStatus;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }
}