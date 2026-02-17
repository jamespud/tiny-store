package com.github.spud.tinystore.promotion.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 订单域事件 DTO
 * 对应 Outbox envelope 结构
 */
public class OrderDomainEventDto {

	@JsonProperty("eventId")
	private String eventId;

	@JsonProperty("eventType")
	private String eventType;

	@JsonProperty("aggregateType")
	private String aggregateType;

	@JsonProperty("aggregateId")
	private String aggregateId;

	@JsonProperty("occurredAt")
	private String occurredAt;

	@JsonProperty("traceId")
	private String traceId;

	@JsonProperty("payload")
	private String payload;

	public OrderDomainEventDto() {
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public void setAggregateType(String aggregateType) {
		this.aggregateType = aggregateType;
	}

	public String getAggregateId() {
		return aggregateId;
	}

	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}

	public String getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(String occurredAt) {
		this.occurredAt = occurredAt;
	}

	public String getTraceId() {
		return traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}
}
