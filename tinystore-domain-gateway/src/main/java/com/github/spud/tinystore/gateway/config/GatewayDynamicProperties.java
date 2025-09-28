package com.github.spud.tinystore.gateway.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties(prefix = "gateway.dynamic")
public class GatewayDynamicProperties {

	@DurationUnit(ChronoUnit.SECONDS)
	private Duration refreshInterval = Duration.ofSeconds(10);

	private String routesLocation = "classpath:gateway-routes.yml";

	public Duration getRefreshInterval() {
		return refreshInterval;
	}

	public void setRefreshInterval(Duration refreshInterval) {
		this.refreshInterval = refreshInterval;
	}

	public String getRoutesLocation() {
		return routesLocation;
	}

	public void setRoutesLocation(String routesLocation) {
		this.routesLocation = routesLocation;
	}
}
