package com.github.spud.tinystore.promotion.application.service;

public interface IdempotencyStorage {

	boolean exists(String key);

	<T> T getResponse(String key, Class<T> type);

	void saveResponse(String key, Object value);

	void evict(String key);
}

