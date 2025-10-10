package com.github.spud.tinystore.auth.infrastructure.persistence.repository;

import com.github.spud.tinystore.auth.domain.model.otp.Otp;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

public class RedisOtpRepositoryPort {

	private StringRedisTemplate redisTemplate;

	public Otp save(Otp otp) {
		throw new UnsupportedOperationException();
	}

	public Optional<Otp> findLatest(PhoneNumber phone) {
		throw new UnsupportedOperationException();
	}

	public void markUsed(Otp otp) {
		throw new UnsupportedOperationException();
	}
}
