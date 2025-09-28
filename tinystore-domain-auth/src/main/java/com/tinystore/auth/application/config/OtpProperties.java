package com.tinystore.auth.application.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "tinystore.auth.otp")
@Validated
public class OtpProperties {

	private Duration ttl = Duration.ofMinutes(5);

	private int maxSendPerWindow = 2;

	private Duration rateWindow = Duration.ofMinutes(1);

	private Duration blockDuration = Duration.ofMinutes(5);

	private Duration requestLockTtl = Duration.ofSeconds(3);

	private Duration requestCacheTtl = Duration.ofMinutes(10);

	public Duration getTtl() {
		return ttl;
	}

	public void setTtl(Duration ttl) {
		this.ttl = ttl;
	}

	public int getMaxSendPerWindow() {
		return maxSendPerWindow;
	}

	public void setMaxSendPerWindow(int maxSendPerWindow) {
		this.maxSendPerWindow = maxSendPerWindow;
	}

	public Duration getRateWindow() {
		return rateWindow;
	}

	public void setRateWindow(Duration rateWindow) {
		this.rateWindow = rateWindow;
	}

	public Duration getBlockDuration() {
		return blockDuration;
	}

	public void setBlockDuration(Duration blockDuration) {
		this.blockDuration = blockDuration;
	}

	public Duration getRequestLockTtl() {
		return requestLockTtl;
	}

	public void setRequestLockTtl(Duration requestLockTtl) {
		this.requestLockTtl = requestLockTtl;
	}

	public Duration getRequestCacheTtl() {
		return requestCacheTtl;
	}

	public void setRequestCacheTtl(Duration requestCacheTtl) {
		this.requestCacheTtl = requestCacheTtl;
	}
}
