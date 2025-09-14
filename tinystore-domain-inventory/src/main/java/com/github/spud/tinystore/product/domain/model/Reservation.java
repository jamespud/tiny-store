package com.github.spud.tinystore.product.domain.model;

import com.github.spud.tinystore.product.domain.enums.ReservationState;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预留记录实体（仅字段与访问器，逻辑在 DomainService 中实现）
 */
@Data
@NoArgsConstructor
public class Reservation {
	private String reservationId;
	private String shopId;
	private String skuId;
	private Integer requestQuantity;
	private ReservationState state;
	private OffsetDateTime expireAt;
	private String operationId; // 幂等标识，可为空
}

