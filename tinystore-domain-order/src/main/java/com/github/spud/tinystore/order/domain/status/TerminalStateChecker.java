package com.github.spud.tinystore.order.domain.status;

import java.util.EnumSet;

/**
 * 终态判定工具
 */
public final class TerminalStateChecker {
    private static final EnumSet<CoreFlowStatus> TERMINALS = EnumSet.of(
            CoreFlowStatus.COMPLETED,
            CoreFlowStatus.CANCELLED,
            CoreFlowStatus.CLOSED,
            CoreFlowStatus.REFUNDED
    );

    private TerminalStateChecker() {
    }

    public static boolean isTerminal(CoreFlowStatus status) {
        return status != null && TERMINALS.contains(status);
    }
}

