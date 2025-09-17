package com.github.spud.tinystore.inventory.domain.model;

/**
 * Reservation 状态机：PENDING -> CONFIRMED / RELEASED / EXPIRED / FAILED
 */
public enum ReservationState {
	PENDING,
	CONFIRMED,
	RELEASED,
	EXPIRED,
	FAILED
}

