package com.github.spud.tinystore.gateway.security;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties(prefix = "security.jwks")
public class JwksProperties {

	private String uri = "http://localhost:9000/.well-known/jwks.json";

	@DurationUnit(ChronoUnit.MINUTES)
	private Duration cacheTtl = Duration.ofMinutes(5);

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public Duration getCacheTtl() {
		return cacheTtl;
	}

	public void setCacheTtl(Duration cacheTtl) {
		this.cacheTtl = cacheTtl;
	}
}
