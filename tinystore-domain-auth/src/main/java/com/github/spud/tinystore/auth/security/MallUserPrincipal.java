package com.github.spud.tinystore.auth.security;

/**
 * @deprecated 已迁移至 {@link com.tinystore.auth.interfaces.security.MallUserPrincipal}
 */
@Deprecated(forRemoval = true)
public final class MallUserPrincipal {
	private MallUserPrincipal() {
		throw new UnsupportedOperationException("Use com.tinystore.auth.interfaces.security.MallUserPrincipal instead.");
	}
}
