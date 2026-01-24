package com.github.spud.tinystore.promotion.application.service;

public interface IdempotencyStorage {

	StoredValue get(String key);

	void put(String key, String requestHash, Object value);

	void evict(String key);

	record StoredValue(String requestHash, Object value) {
	}
}
