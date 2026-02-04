package com.github.spud.tinystore.gateway.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
// @EnableWebFluxSecurity  // Temporarily disabled for testing
@ConditionalOnProperty(prefix = "tinystore.security.resourceserver", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SecurityConfig {

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder,
		MetricsAuthenticationEntryPoint authenticationEntryPoint,
		MetricsAccessDeniedHandler accessDeniedHandler) {
		return http
			.csrf(ServerHttpSecurity.CsrfSpec::disable)
			.authorizeExchange(exchanges -> exchanges
				.pathMatchers("/actuator/health", "/actuator/info").permitAll()
				.pathMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
				.pathMatchers(HttpMethod.POST, "/actuator/gateway-refresh").hasAuthority("SCOPE_gateway.admin")
				.anyExchange().authenticated())
			.exceptionHandling(spec -> spec
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
			.build();
	}

	@Bean
	public ReactiveJwtDecoder reactiveJwtDecoder(JwkCacheService jwkCacheService) {
		return jwkCacheService;
	}
}
