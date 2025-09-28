package com.github.spud.tinystore.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
@SpringBootApplication(scanBasePackages = "com.github.spud.tinystore")
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties({ GatewayDynamicProperties.class, JwksProperties.class, IdempotencyProperties.class })
public class GatewayApplication {
	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}
}