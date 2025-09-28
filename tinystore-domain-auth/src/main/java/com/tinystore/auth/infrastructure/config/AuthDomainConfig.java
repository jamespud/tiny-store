package com.tinystore.auth.infrastructure.config;

import com.tinystore.auth.domain.service.OtpGenerationService;
import com.tinystore.auth.domain.service.RefreshTokenVersionService;
import com.tinystore.auth.domain.service.ScopePolicyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AuthDomainConfig {

	@Bean
	public OtpGenerationService otpGenerationService(@Value("${tinystore.auth.otp.ttl:PT5M}") Duration ttl) {
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