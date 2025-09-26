package com.github.spud.tinystore.order.domain.repository;

import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for managing idempotency keys to prevent duplicate processing
 *
 * @author Spud
 * @date 2025/9/22
 */
@Repository
public interface IdempotencyRepository {

	/**
	 * Try to acquire an idempotency lock
	 *
	 * @param key   Idempotency key (typically callback ID or content hash)
	 * @param owner Owner of the lock (typically service instance ID)
	 * @param ttl   Time to live for the lock
	 * @return true if lock acquired successfully, false if already exists
	 */
	boolean tryAcquire(String key, String owner, Duration ttl);

	/**
	 * Release an idempotency lock
	 *
	 * @param key   Idempotency key to release
	 * @param owner Owner who acquired the lock
	 * @return true if released successfully, false if key not found or wrong owner
	 */
	boolean release(String key, String owner);

	/**
	 * Check if idempotency key exists
	 *
	 * @param key Idempotency key to check
	 * @return true if key exists (regardless of owner)
	 */
	boolean exists(String key);

	/**
	 * Get idempotency record details
	 *
	 * @param key Idempotency key to lookup
	 * @return Idempotency record if exists
	 */
	Optional<IdempotencyRecord> findByKey(String key);

	/**
	 * Clean up expired idempotency records
	 *
	 * @param beforeTime Delete records created before this time
	 * @return Number of records deleted
	 */
	int deleteExpiredBefore(LocalDateTime beforeTime);

	/**
	 * Idempotency record
	 */
	class IdempotencyRecord {
		private final String key;
		private final String owner;
		private final LocalDateTime createdAt;
		private final LocalDateTime expiresAt;
		private final String result; // Optional: cached result for idempotent responses

		public IdempotencyRecord(String key, String owner, LocalDateTime createdAt,
		                         LocalDateTime expiresAt, String result) {
			this.key = key;
			this.owner = owner;
			this.createdAt = createdAt;
			this.expiresAt = expiresAt;
			this.result = result;
		}

		public String getKey() {
			return key;
		}

		public String getOwner() {
			return owner;
		}

		public LocalDateTime getCreatedAt() {
			return createdAt;
		}

		public LocalDateTime getExpiresAt() {
			return expiresAt;
		}

		public String getResult() {
			return result;
		}

		public boolean isExpired() {
			return LocalDateTime.now().isAfter(expiresAt);
		}

		public boolean isOwnedBy(String owner) {
			return this.owner.equals(owner);
		}
	}

	/**
	 * Idempotency key generators for different callback types
	 */
	class IdempotencyKeyGenerator {

		/**
		 * Generate key for payment callbacks
		 * Uses third-party payment ID or fallback to content hash
		 */
		public static String forPaymentCallback(String paymentId, String orderId, String amount) {
			if (paymentId != null && !paymentId.trim().isEmpty()) {
				return "payment:" + paymentId;
			}
			return "payment_hash:" + (orderId + ":" + amount).hashCode();
		}

		/**
		 * Generate key for refund callbacks
		 */
		public static String forRefundCallback(String refundId, String orderId) {
			if (refundId != null && !refundId.trim().isEmpty()) {
				return "refund:" + refundId;
			}
			return "refund_hash:" + orderId.hashCode();
		}

		/**
		 * Generate key for logistics callbacks
		 * Uses content hash since logistics systems often don't provide stable IDs
		 */
		public static String forLogisticsCallback(String orderId, String status, String timestamp) {
			String content = orderId + ":" + status + ":" + timestamp;
			return "logistics_hash:" + content.hashCode();
		}

		/**
		 * Generate key for generic callbacks with content hash
		 */
		public static String forContentHash(String prefix, String... contentParts) {
			String content = String.join(":", contentParts);
			return prefix + "_hash:" + content.hashCode();
		}
	}
}