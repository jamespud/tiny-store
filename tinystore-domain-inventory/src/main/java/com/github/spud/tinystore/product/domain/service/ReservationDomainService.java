package com.github.spud.tinystore.product.domain.service;

import com.github.spud.tinystore.product.domain.enums.ReservationState;
import com.github.spud.tinystore.product.domain.model.Reservation;
import org.springframework.stereotype.Service;

/**
 * 预留领域服务（状态流转、创建占位）
 * 状态迁移矩阵（PENDING -> CONFIRMED / RELEASED / EXPIRED / FAILED）
 * TODO(inv): 实现校验逻辑：
 *  - 仅 PENDING 可转 CONFIRMED/RELEASED/EXPIRED/FAILED
 *  - 终态（CONFIRMED/RELEASED/EXPIRED/FAILED）不可再变更
 */
@Service
public class ReservationDomainService {
	public Reservation createPendingReservation(String shopId, String skuId, int quantity, Integer expireSeconds, String operationId) {
		// TODO(inv): 创建 Reservation（生成 UUID reservationId, 计算 expireAt）
		return null;
	}

	public boolean transition(Reservation reservation, ReservationState target) {
		// TODO(inv): 校验合法性并返回结果
		return false;
	}
}
