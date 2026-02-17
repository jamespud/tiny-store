package com.github.spud.tinystore.promotion.infrastructure.persistence.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consumer_event_log", schema = "promotion")
public class ConsumerEventLogEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "event_id", nullable = false)
	private String eventId;

	@Column(name = "consumer_name", nullable = false)
	private String consumerName;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "processed_at", nullable = false)
	private LocalDateTime processedAt;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public ConsumerEventLogEntity() {
	}

	public ConsumerEventLogEntity(UUID id, String eventId, String consumerName, String status,
		LocalDateTime processedAt, String errorMessage, LocalDateTime createdAt) {
		this.id = id;
		this.eventId = eventId;
		this.consumerName = consumerName;
		this.status = status;
		this.processedAt = processedAt;
		this.errorMessage = errorMessage;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getConsumerName() {
		return consumerName;
	}

	public void setConsumerName(String consumerName) {
		this.consumerName = consumerName;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(LocalDateTime processedAt) {
		this.processedAt = processedAt;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
