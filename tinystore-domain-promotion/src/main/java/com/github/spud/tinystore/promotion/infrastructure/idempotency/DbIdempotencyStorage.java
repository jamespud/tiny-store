package com.github.spud.tinystore.promotion.infrastructure.idempotency;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.promotion.application.service.IdempotencyConflictException;
import com.github.spud.tinystore.promotion.application.service.IdempotencyStorage;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity.IdempotencyRecordEntity;
import com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.repository.JpaIdempotencyRecordRepository;

@Component
@Primary
public class DbIdempotencyStorage implements IdempotencyStorage {

	private final JpaIdempotencyRecordRepository repository;
	private final ObjectMapper objectMapper;
	private final long ttlSeconds;

	public DbIdempotencyStorage(JpaIdempotencyRecordRepository repository,
		ObjectMapper objectMapper,
		@Value("${promotion.idempotency.ttl-seconds:600}") long ttlSeconds) {
		this.repository = repository;
		this.objectMapper = objectMapper;
		this.ttlSeconds = ttlSeconds;
	}

	@Override
	@Transactional
	public StoredValue get(String key) {
		ParsedKey parsedKey = parseKey(key);
		Optional<IdempotencyRecordEntity> opt = repository.findByOpAndIdempotencyKey(parsedKey.op(), parsedKey.idempotencyKey());
		if (opt.isEmpty()) {
			return null;
		}
		IdempotencyRecordEntity entity = opt.get();
		if (!entity.getExpiresAt().isAfter(LocalDateTime.now())) {
			repository.delete(entity);
			return null;
		}
		try {
			JsonNode value = objectMapper.readTree(entity.getResponseJson());
			return new StoredValue(entity.getRequestHash(), value);
		} catch (Exception e) {
			throw new IllegalStateException("failed to deserialize idempotency response", e);
		}
	}

	@Override
	@Transactional
	public void put(String key, String requestHash, Object value) {
		Objects.requireNonNull(key, "idempotency key must not be null");
		Objects.requireNonNull(requestHash, "request hash must not be null");
		ParsedKey parsedKey = parseKey(key);
		LocalDateTime now = LocalDateTime.now();
		String responseJson;
		try {
			responseJson = objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException("failed to serialize idempotency response", e);
		}

		IdempotencyRecordEntity entity = repository.findByOpAndIdempotencyKey(parsedKey.op(), parsedKey.idempotencyKey())
			.orElseGet(() -> {
				IdempotencyRecordEntity created = new IdempotencyRecordEntity();
				created.setId(UUID.randomUUID());
				created.setOp(parsedKey.op());
				created.setIdempotencyKey(parsedKey.idempotencyKey());
				created.setCreatedAt(now);
				return created;
			});
		if (entity.getRequestHash() != null && !entity.getRequestHash().equals(requestHash)) {
			throw new IdempotencyConflictException("idempotency conflict when storing response");
		}
		entity.setRequestHash(requestHash);
		entity.setResponseJson(responseJson);
		entity.setExpiresAt(now.plusSeconds(ttlSeconds));
		entity.setUpdatedAt(now);
		repository.save(entity);
	}

	@Override
	@Transactional
	public void evict(String key) {
		ParsedKey parsedKey = parseKey(key);
		repository.findByOpAndIdempotencyKey(parsedKey.op(), parsedKey.idempotencyKey())
			.ifPresent(repository::delete);
	}

	private ParsedKey parseKey(String key) {
		String[] parts = key.split(":", 4);
		if (parts.length < 4) {
			throw new IllegalArgumentException("invalid idempotency key: " + key);
		}
		return new ParsedKey(parts[2], parts[3]);
	}

	private record ParsedKey(String op, String idempotencyKey) {
	}
}
