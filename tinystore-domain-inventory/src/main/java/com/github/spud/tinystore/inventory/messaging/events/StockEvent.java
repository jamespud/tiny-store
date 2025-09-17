package com.github.spud.tinystore.inventory.messaging.events;

import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存事件基类（仅数据结构占位）
 */
@Data
@NoArgsConstructor
public class StockEvent {

	private String type; // RESERVED/RELEASED/CONFIRMED/TOTAL_ADJUST
	private String shopId;
	private String skuId;
	private Integer deltaTotal; // 允许为 null
	private Integer deltaReserved; // 允许为 null
	private String reservationId; // 预留相关事件填充
	private Integer version; // DB 期望版本
	private String correlationId; // 订单号等
	private Instant ts; // 事件时间
}

