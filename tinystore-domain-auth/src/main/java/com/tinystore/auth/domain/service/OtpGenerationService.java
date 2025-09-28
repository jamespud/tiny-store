package com.tinystore.auth.domain.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.tinystore.auth.domain.model.otp.Otp;
import com.tinystore.auth.domain.primitives.OtpCode;
import com.tinystore.auth.domain.primitives.PhoneNumber;

public class OtpGenerationService {

	private static final char[] DIGITS = "0123456789".toCharArray();

	private final SecureRandom random = new SecureRandom();
	private final int length;
	private final Duration ttl;

	public OtpGenerationService(int length, Duration ttl) {
		if (length < 4) {
			throw new IllegalArgumentException("otp length must be >= 4");
		}
		this.length = length;
		this.ttl = ttl;
	}

	public Otp generate(PhoneNumber phone) {
		StringBuilder builder = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			builder.append(DIGITS[random.nextInt(DIGITS.length)]);
		}
		OtpCode code = OtpCode.of(builder.toString());
		OffsetDateTime expireAt = OffsetDateTime.now().plus(ttl);
		return Otp.create(phone, code, expireAt);
	}

	public void verify(Otp otp, OtpCode input) {
		otp.ensureValid(input);
		otp.markUsed();
	}
}