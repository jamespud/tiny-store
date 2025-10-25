package com.github.spud.tinystore.auth.infrastructure.config;

import com.github.spud.tinystore.auth.application.config.DynamicRegistrationProperties;
import com.github.spud.tinystore.auth.application.config.OtpProperties;
import com.github.spud.tinystore.auth.application.config.OutboxProperties;
import com.github.spud.tinystore.auth.domain.service.OtpGenerationService;
import com.github.spud.tinystore.auth.domain.service.RefreshTokenVersionService;
import com.github.spud.tinystore.auth.domain.service.ScopePolicyService;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OtpProperties.class, OutboxProperties.class, DynamicRegistrationProperties.class})
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