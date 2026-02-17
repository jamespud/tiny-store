package com.github.spud.tinystore.order.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin Security Configuration Properties
 *
 * <p>Provides configuration for internal admin endpoints security.
 */
@ConfigurationProperties(prefix = "tinystore.order.admin")
public record AdminSecurityProperties(String token) {
}
