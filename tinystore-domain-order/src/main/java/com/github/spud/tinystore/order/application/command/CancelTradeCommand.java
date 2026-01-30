package com.github.spud.tinystore.order.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 取消交易命令
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelTradeCommand {
    private String tradeId;
    private String reason;
    private String traceId;
}
