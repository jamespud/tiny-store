package com.github.spud.tinystore.order.domain.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/**
 * 仅内部调试：导出核心生命周期 DOT
 */
public class OrderStateGraphExporter {
    private static final String DOT = "digraph ORDER_STATE_MACHINE {\n" +
            "  rankdir=LR;\n" +
            "  subgraph cluster_core {\n" +
            "    label=\"CORE_FLOW\";style=filled;color=lightgrey;\n" +
            "    PENDING_PAYMENT;PENDING_FINAL_PAYMENT;PAID_CONFIRMED;AWAITING_FULFILLMENT;FULFILLING;AFTER_SALE;CANCELLING;COMPLETED;CANCELLED;CLOSED;REFUNDED;\n" +
            "  }\n" +
            "  PENDING_PAYMENT -> PAID_CONFIRMED [label=\"pay full\"];\n" +
            "  PENDING_PAYMENT -> PENDING_FINAL_PAYMENT [label=\"pay deposit\"];\n" +
            "  PENDING_FINAL_PAYMENT -> PAID_CONFIRMED [label=\"final pay\"];\n" +
            "  PENDING_FINAL_PAYMENT -> CANCELLED [label=\"timeout\"];\n" +
            "  PAID_CONFIRMED -> AWAITING_FULFILLMENT;\n" +
            "  AWAITING_FULFILLMENT -> FULFILLING [label=\"first outbound\"];\n" +
            "  FULFILLING -> AFTER_SALE [label=\"after-sale request\"];\n" +
            "  COMPLETED -> AFTER_SALE [label=\"after-sale window\"];\n" +
            "  FULFILLING -> COMPLETED [label=\"delivered & no after-sale\"];\n" +
            "  FULFILLING -> CANCELLING [label=\"cancel req\"];\n" +
            "  CANCELLING -> CANCELLED [label=\"approve\"];\n" +
            "  CANCELLING -> FULFILLING [label=\"reject\"];\n" +
            "  AFTER_SALE -> REFUNDED [label=\"refund success\"];\n" +
            "  AFTER_SALE -> COMPLETED [label=\"exchange done\"];\n" +
            "  ANY -> CLOSED [label=\"risk\"];\n" +
            "}\n";

    public static void export(Path path) throws IOException {
        Files.writeString(path, DOT);
    }

    public static String dot() {
        return DOT;
    }
}

