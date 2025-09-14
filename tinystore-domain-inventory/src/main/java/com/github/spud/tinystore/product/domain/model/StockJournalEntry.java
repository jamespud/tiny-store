package com.github.spud.tinystore.product.domain.model;

import com.github.spud.tinystore.product.domain.enums.StockJournalType;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存流水记录（仅数据结构）
 */
@Data
@NoArgsConstructor
public class StockJournalEntry {
	private String shopId;
	private String skuId;
	private StockJournalType type;
	private Integer deltaTotal;
	private Integer deltaReserved;
	private String reservationId;
	private String correlationId;
	private OffsetDateTime createdAt;
}

