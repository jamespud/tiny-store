package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "checkout_quote", schema = "promotion")
public class CheckoutQuoteEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "input_hash", nullable = false)
	private String inputHash;

	@Column(name = "pricing_rules_version")
	private String pricingRulesVersion;

	@Column(name = "shipping_rules_version")
	private String shippingRulesVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
	private String snapshot;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "trade_id")
	private String tradeId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getInputHash() {
		return inputHash;
	}

	public void setInputHash(String inputHash) {
		this.inputHash = inputHash;
	}

	public String getPricingRulesVersion() {
		return pricingRulesVersion;
	}

	public void setPricingRulesVersion(String pricingRulesVersion) {
		this.pricingRulesVersion = pricingRulesVersion;
	}

	public String getShippingRulesVersion() {
		return shippingRulesVersion;
	}

	public void setShippingRulesVersion(String shippingRulesVersion) {
		this.shippingRulesVersion = shippingRulesVersion;
	}

	public String getSnapshot() {
		return snapshot;
	}

	public void setSnapshot(String snapshot) {
		this.snapshot = snapshot;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(LocalDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public String getTradeId() {
		return tradeId;
	}

	public void setTradeId(String tradeId) {
		this.tradeId = tradeId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
