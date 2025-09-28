package com.github.spud.tinystore.gateway.idempotency;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {

	private String keyPrefix = "idempotent";

	@DurationUnit(ChronoUnit.SECONDS)
	private Duration ttl = Duration.ofMinutes(5);

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}

	public Duration getTtl() {
		return ttl;
	}

	public void setTtl(Duration ttl) {
		this.ttl = ttl;
	}
}
