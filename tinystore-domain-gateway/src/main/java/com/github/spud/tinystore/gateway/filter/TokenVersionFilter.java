package com.github.spud.tinystore.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.github.spud.tinystore.gateway.metrics.GatewayMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Component
public class TokenVersionFilter implements GlobalFilter, Ordered {

    private static final String KEY_PREFIX = "tinystore:security:credential-version:";
    private static final Duration BOOTSTRAP_TTL = Duration.ofDays(365);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    public TokenVersionFilter(ReactiveStringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(Authentication.class)
                .flatMap(authentication -> handleAuthenticated(exchange, chain, authentication).thenReturn(true))
                .switchIfEmpty(chain.filter(exchange).thenReturn(true))
                .then();
    }

    private Mono<Void> handleAuthenticated(ServerWebExchange exchange, GatewayFilterChain chain, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return chain.filter(exchange);
        }

        String userId = jwtAuth.getToken().getClaimAsString("user_id");
        if (!StringUtils.hasText(userId)) {
            userId = jwtAuth.getName();
        }
        Long tokenVersion = toLong(jwtAuth.getToken().getClaims().get("rt_version"));
        if (!StringUtils.hasText(userId) || tokenVersion == null) {
            return chain.filter(exchange);
        }

        String key = KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key)
                .defaultIfEmpty("")
                .flatMap(stored -> {
                    if (!StringUtils.hasText(stored)) {
                        return redisTemplate.opsForValue()
                                .setIfAbsent(key, String.valueOf(tokenVersion), BOOTSTRAP_TTL)
                                .then(chain.filter(exchange));
                    }
                    try {
                        long storedVersion = Long.parseLong(stored);
                        if (storedVersion > tokenVersion) {
                            meterRegistry.counter(GatewayMetrics.AUTH_FAILURES, "reason", "token_revoked")
                                    .increment();
                            return respondRevoked(exchange);
                        }
                        return chain.filter(exchange);
                    } catch (NumberFormatException ex) {
                        return chain.filter(exchange);
                    }
                })
                .onErrorResume(ex -> chain.filter(exchange));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Mono<Void> respondRevoked(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = "{\"error\":\"token_revoked\"}".getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 60;
    }
}
