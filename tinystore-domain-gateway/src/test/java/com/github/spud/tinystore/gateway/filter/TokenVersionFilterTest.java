package com.github.spud.tinystore.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TokenVersionFilterTest {

    @Test
    void shouldRejectWhenStoredVersionIsGreaterThanTokenVersion() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(Mono.just("6"));
        when(ops.setIfAbsent(anyString(), anyString(), any())).thenReturn(Mono.just(true));

        TokenVersionFilter filter = new TokenVersionFilter(redisTemplate, new SimpleMeterRegistry());

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("user_id", "1")
                .claim("rt_version", 5)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_read")), "user-1");

        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build())
                .mutate()
                .principal(Mono.just(authenticationToken))
                .build();

        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not be called"));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("token_revoked");

        verify(ops, never()).setIfAbsent(anyString(), anyString(), any());
    }

    @Test
    void shouldPassWhenNoStoredVersion() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(Mono.empty());
        when(ops.setIfAbsent(anyString(), anyString(), any())).thenReturn(Mono.just(true));

        TokenVersionFilter filter = new TokenVersionFilter(redisTemplate, new SimpleMeterRegistry());

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("user_id", "1")
                .claim("rt_version", 5)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_read")), "user-1");

        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build())
                .mutate()
                .principal(Mono.just(authenticationToken))
                .build();

        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        verify(ops).setIfAbsent(anyString(), anyString(), any());
    }
}
