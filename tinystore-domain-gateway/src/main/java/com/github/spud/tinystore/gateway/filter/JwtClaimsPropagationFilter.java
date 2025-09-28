package com.github.spud.tinystore.gateway.filter;

import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class JwtClaimsPropagationFilter implements GlobalFilter, Ordered {

	public static final String HEADER_SUB = "X-Tinystore-Sub";
	public static final String HEADER_ROLES = "X-Tinystore-Roles";
	public static final String HEADER_CLIENT_ID = "X-Tinystore-ClientId";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return exchange.getPrincipal()
			.cast(Authentication.class)
			.flatMap(authentication -> {
				ServerWebExchange targetExchange = exchange;
				if (authentication instanceof JwtAuthenticationToken jwtAuth) {
					ServerWebExchange.Builder builder = exchange.mutate();
					builder.request(requestBuilder -> requestBuilder.headers(headers -> {
						headers.remove(HttpHeaders.AUTHORIZATION);
						headers.set(HEADER_SUB, jwtAuth.getName());
						String roles = jwtAuth.getAuthorities().stream()
							.map(granted -> granted.getAuthority())
							.collect(Collectors.joining(","));
						headers.set(HEADER_ROLES, roles);
						Object clientId = jwtAuth.getToken().getClaims().get("client_id");
						if (clientId != null) {
							headers.set(HEADER_CLIENT_ID, clientId.toString());
						}
					}));
					targetExchange = builder.build();
				}
				return chain.filter(targetExchange);
			})
			.switchIfEmpty(chain.filter(exchange).then(Mono.empty()));
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 50;
	}
}
