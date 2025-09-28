package com.github.spud.tinystore.gateway.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class MetricsConfig {

	@Bean
	MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer(
		@Value("${spring.application.name:tinystore-gateway}") String applicationName) {
		return registry -> registry.config().commonTags("application", applicationName);
	}
}
