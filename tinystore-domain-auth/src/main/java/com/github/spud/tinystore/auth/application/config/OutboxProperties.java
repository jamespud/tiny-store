package com.github.spud.tinystore.auth.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "tinystore.auth.outbox")
@Validated
public class OutboxProperties {

	private boolean enabled = true;

	private Duration publishInterval = Duration.ofSeconds(5);

	private int batchSize = 100;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Duration getPublishInterval() {
		return publishInterval;
	}

	public void setPublishInterval(Duration publishInterval) {
		this.publishInterval = publishInterval;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}
}
