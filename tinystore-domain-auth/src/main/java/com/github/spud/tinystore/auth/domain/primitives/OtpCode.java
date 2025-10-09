package com.github.spud.tinystore.auth.domain.primitives;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * OTP 验证码值对象。
 */
public final class OtpCode implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String value;

	private OtpCode(String value) {
		String normalized = Objects.requireNonNull(value, "otp must not be null").trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("otp must not be blank");
		}
		this.value = normalized;
	}

	public static OtpCode of(String value) {
		return new OtpCode(value);
	}

	public String getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OtpCode otpCode)) {
			return false;
		}
		return value.equals(otpCode.value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return value;
	}
}