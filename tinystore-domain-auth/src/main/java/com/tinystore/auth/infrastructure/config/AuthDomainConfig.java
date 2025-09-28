package com.tinystore.auth.infrastructure.config;

import com.tinystore.auth.application.config.OtpProperties;
import com.tinystore.auth.application.config.OutboxProperties;
import com.tinystore.auth.domain.service.OtpGenerationService;
import com.tinystore.auth.domain.service.RefreshTokenVersionService;
import com.tinystore.auth.domain.service.ScopePolicyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({OtpProperties.class, OutboxProperties.class})
public class AuthDomainConfig {

	@Bean
	public OtpGenerationService otpGenerationService(OtpProperties otpProperties) {
		Duration ttl = otpProperties.getTtl();
		return new OtpGenerationService(6, ttl);
	}

	@Bean
	public RefreshTokenVersionService refreshTokenVersionService() {
		return new RefreshTokenVersionService();
	}

	@Bean
	public ScopePolicyService scopePolicyService() {
		return new ScopePolicyService();
	}
}