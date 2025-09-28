package com.github.spud.tinystore.gateway.security;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class JwkCacheService implements ReactiveJwtDecoder {

	private static final Logger log = LoggerFactory.getLogger(JwkCacheService.class);

	private final JwksProperties jwksProperties;
	private final AtomicReference<DecoderHolder> holder = new AtomicReference<>();

	public JwkCacheService(JwksProperties jwksProperties) {
		this.jwksProperties = jwksProperties;
	}

	@Override
	public Mono<Jwt> decode(String token) throws JwtException {
		return currentDecoder().decode(token);
	}

	private ReactiveJwtDecoder currentDecoder() {
		DecoderHolder current = holder.get();
		Instant now = Instant.now();
		if (current == null || current.expiresAt().isBefore(now)) {
			return refreshDecoder(now);
		}
		return current.decoder();
	}

	private ReactiveJwtDecoder refreshDecoder(Instant now) {
		synchronized (holder) {
			DecoderHolder current = holder.get();
			if (current != null && current.expiresAt().isAfter(now)) {
				return current.decoder();
			}
			ReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwksProperties.getUri()).build();
			Instant expiresAt = now.plus(jwksProperties.getCacheTtl());
			holder.set(new DecoderHolder(decoder, expiresAt));
			log.info("Refreshed JWKS decoder, valid until {}", expiresAt);
			return decoder;
		}
	}

	private record DecoderHolder(ReactiveJwtDecoder decoder, Instant expiresAt) {
	}
}
