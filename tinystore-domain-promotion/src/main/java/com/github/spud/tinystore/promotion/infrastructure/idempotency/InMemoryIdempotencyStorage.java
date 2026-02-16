package com.github.spud.tinystore.promotion.infrastructure.idempotency;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.spud.tinystore.promotion.application.service.IdempotencyStorage;

@Component
public class InMemoryIdempotencyStorage implements IdempotencyStorage {

	private final long ttlSeconds;
	private final Map<String, Entry> store = new ConcurrentHashMap<>();

	public InMemoryIdempotencyStorage(@Value("${promotion.idempotency.ttl-seconds:600}") long ttlSeconds) {
		this.ttlSeconds = ttlSeconds;
	}

	@Override
	public StoredValue get(String key) {
		Entry e = store.get(key);
		if (e == null) {
			return null;
		}
		if (e.expireAt < Instant.now().getEpochSecond()) {
			store.remove(key);
			return null;
		}
		return new StoredValue(e.requestHash, e.value);
	}

	@Override
	public void put(String key, String requestHash, Object value) {
		Objects.requireNonNull(key, "idempotency key must not be null");
		Objects.requireNonNull(requestHash, "request hash must not be null");
		long expireAt = Instant.now().getEpochSecond() + ttlSeconds;
		store.put(key, new Entry(requestHash, value, expireAt));
	}

	@Override
	public void evict(String key) {
		store.remove(key);
	}

	private record Entry(String requestHash, Object value, long expireAt) {
	}
}
