package com.github.spud.tinystore.auth.security.otp;

/**
 * @deprecated 已迁移至 {@link com.tinystore.auth.interfaces.security.otp.OtpAuthenticationProvider}
 */
@Deprecated(forRemoval = true)
public final class OtpAuthenticationProvider {
	private OtpAuthenticationProvider() {
		throw new UnsupportedOperationException("Use com.tinystore.auth.interfaces.security.otp.OtpAuthenticationProvider instead.");
	}
}
