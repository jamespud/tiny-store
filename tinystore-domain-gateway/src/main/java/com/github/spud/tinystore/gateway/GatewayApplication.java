package com.github.spud.tinystore.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.github.spud.tinystore.gateway.config.GatewayDynamicProperties;
import com.github.spud.tinystore.gateway.idempotency.IdempotencyProperties;
import com.github.spud.tinystore.gateway.security.JwksProperties;

/**
 * @author Spud
 * @date 2025/9/17
 */
@SpringBootApplication(
	scanBasePackages = {
		"com.github.spud.tinystore.gateway",
		"com.github.spud.tinystore.infrastructure.config",
		"com.github.spud.tinystore.infrastructure.redis"
	},
	exclude = {
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class,
		ReactiveOAuth2ResourceServerAutoConfiguration.class,
		ReactiveSecurityAutoConfiguration.class,
		ReactiveUserDetailsServiceAutoConfiguration.class,
		ReactiveManagementWebSecurityAutoConfiguration.class
	}
)
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties({ GatewayDynamicProperties.class, JwksProperties.class, IdempotencyProperties.class })
public class GatewayApplication {
	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}
}