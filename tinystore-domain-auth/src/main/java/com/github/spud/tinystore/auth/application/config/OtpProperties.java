package com.github.spud.tinystore.auth.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Setter
@Getter
@ConfigurationProperties(prefix = "tinystore.auth.otp")
@Validated
public class OtpProperties {

	private Duration ttl = Duration.ofMinutes(5);

	private int maxSendPerWindow = 2;

	private Duration rateWindow = Duration.ofMinutes(1);

	private Duration blockDuration = Duration.ofMinutes(5);

	private Duration requestLockTtl = Duration.ofSeconds(3);

	private Duration requestCacheTtl = Duration.ofMinutes(10);

}
