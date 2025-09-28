package com.github.spud.tinystore.gateway.filter;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtClaimsPropagationFilterTest {

	private final JwtClaimsPropagationFilter filter = new JwtClaimsPropagationFilter();

	@Test
	void shouldPropagateClaimsAndRemoveAuthorizationHeader() {
		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.claim("sub", "user-1")
			.claim("client_id", "client-123")
			.build();
		JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt,
			List.of(new SimpleGrantedAuthority("SCOPE_read")), "user-1");

		MockServerHttpRequest request = MockServerHttpRequest.get("/test")
			.header(HttpHeaders.AUTHORIZATION, "Bearer token")
			.build();

		ServerWebExchange exchange = MockServerWebExchange.from(request)
			.mutate()
			.principal(Mono.just(authenticationToken))
			.build();

		AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
		GatewayFilterChain chain = mutatedExchange -> {
			captured.set(mutatedExchange);
			return Mono.empty();
		};

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

		ServerWebExchange mutated = captured.get();
		assertThat(mutated.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
		assertThat(mutated.getRequest().getHeaders().getFirst(JwtClaimsPropagationFilter.HEADER_SUB)).isEqualTo("user-1");
		assertThat(mutated.getRequest().getHeaders().getFirst(JwtClaimsPropagationFilter.HEADER_ROLES)).isEqualTo("SCOPE_read");
		assertThat(mutated.getRequest().getHeaders().getFirst(JwtClaimsPropagationFilter.HEADER_CLIENT_ID)).isEqualTo("client-123");
	}
}
