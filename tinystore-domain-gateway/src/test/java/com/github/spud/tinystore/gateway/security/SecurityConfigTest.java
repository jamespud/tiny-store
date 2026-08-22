package com.github.spud.tinystore.gateway.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * A1：网关默认强制 JWT 鉴权。未认证请求访问业务路径应返回 401。
 */
@WebFluxTest(controllers = SecurityConfigTest.ProtectedController.class)
@Import({SecurityConfig.class, MetricsAuthenticationEntryPoint.class, MetricsAccessDeniedHandler.class,
	SecurityConfigTest.TestMeterConfig.class})
@TestPropertySource(properties = "tinystore.security.resourceserver.enabled=true")
@DisplayName("Gateway SecurityConfig — mandatory auth by default (A1)")
class SecurityConfigTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private ReactiveJwtDecoder jwtDecoder;

	@TestConfiguration
	static class TestMeterConfig {
		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}
	}

	@RestController
	static class ProtectedController {
		@GetMapping("/api/protected")
		Mono<String> ok() {
			return Mono.just("ok");
		}
	}

	@Test
	@DisplayName("unauthenticated request to business path returns 401")
	void unauthenticated_returns401() {
		webTestClient.get().uri("/api/protected").exchange()
			.expectStatus().isUnauthorized();
	}
}
