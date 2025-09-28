package com.github.spud.tinystore.auth.security.otp;

/**
 * @deprecated 已迁移至 {@link com.tinystore.auth.interfaces.security.otp.OtpAuthenticationToken}
 */
@Deprecated(forRemoval = true)
public final class OtpAuthenticationToken {
	private OtpAuthenticationToken() {
		throw new UnsupportedOperationException("Use com.tinystore.auth.interfaces.security.otp.OtpAuthenticationToken instead.");
	}
}
