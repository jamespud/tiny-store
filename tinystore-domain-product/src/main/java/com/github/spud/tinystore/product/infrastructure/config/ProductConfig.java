package com.github.spud.tinystore.product.infrastructure.config;

import com.github.spud.tinystore.product.infrastructure.event.LoggingEventPublisher;
import com.github.spud.tinystore.product.infrastructure.outbox.OutboxServiceBridge;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * ProductConfig - Application configuration for Product domain
 * <p>
 * Responsibilities: - Wire domain services (ProductService, PricingService) - Configure repository
 * implementations (JPA vs InMemory based on profile) - Configure event publisher (Outbox vs Logging
 * based on feature toggle) - Configure cache manager and TTL settings - Enable dynamic
 * configuration refresh via @RefreshScope
 * <p>
 * Feature toggles: - feature.outbox.enabled: Use OutboxServiceBridge or LoggingEventPublisher -
 * feature.pricing-rule.enabled: Enable/disable pricing rule execution
 * <p>
 * Profiles: - local/test: Use InMemory repositories - prod: Use JPA repositories
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.github.spud.tinystore.product.infrastructure.persistence.jpa.repository")
@EnableCaching
public class ProductConfig {

	@Value("${tinystore.feature.outbox.enabled:false}")
	private boolean outboxEnabled;

	/**
	 * Configure DomainEventPublisher based on feature toggle
	 *
	 * @param outboxServiceBridge   Outbox publisher implementation
	 * @param loggingEventPublisher Logging fallback publisher
	 * @return Active event publisher
	 */
	@Bean
	@RefreshScope
	public Object domainEventPublisher(
		OutboxServiceBridge outboxServiceBridge,
		ObjectProvider<LoggingEventPublisher> loggingEventPublisherProvider) {
		// TODO: Return actual DomainEventPublisher interface
		// For now, return the appropriate implementation based on toggle
		if (outboxEnabled) {
			return outboxServiceBridge;
		} else {
			LoggingEventPublisher p = loggingEventPublisherProvider.getIfAvailable(LoggingEventPublisher::new);
			return p;
		}
	}
}
