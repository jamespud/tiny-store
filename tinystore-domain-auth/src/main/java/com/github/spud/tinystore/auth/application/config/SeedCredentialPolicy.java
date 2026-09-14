package com.github.spud.tinystore.auth.application.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.Environment;

/**
 * Review round 3 P1: the single place that decides whether the repository's demo credentials may be seeded.
 *
 * <p>Two of the three seeded clients carry a secret anyone can read from this repository:
 * {@code tinystore-internal} defaults to {@code changeit}, and {@code tinystore-tool} used to be hardcoded to
 * {@code changeit} with the {@code svc.admin} scope. {@link DefaultCredentialGuard} refuses to start a
 * {@code prod} instance without a real internal secret, but the tool client was only warned about -- so a
 * production deployment still ended up with a publicly known admin-scoped credential.
 *
 * <p>Shared by the guard (which fails the context) and {@link RegisteredClientConfig} (which seeds) so both
 * sides of the decision cannot drift.
 */
final class SeedCredentialPolicy {

	static final String DEMO_SECRET = "changeit";

	static final String INTERNAL_SECRET_ENV = "TINYSTORE_INTERNAL_CLIENT_SECRET";

	static final String TOOL_SECRET_ENV = "TINYSTORE_TOOL_CLIENT_SECRET";

	static final String TOOL_ENABLED_ENV = "TINYSTORE_TOOL_CLIENT_ENABLED";

	static final String TOOL_ENABLED_PROPERTY = "tinystore.auth.tool-client.enabled";

	private SeedCredentialPolicy() {
	}

	static boolean isProduction(Environment environment) {
		List<String> profiles = Arrays.asList(environment.getActiveProfiles());
		return profiles.contains("prod") || profiles.contains("production");
	}

	/**
	 * Whether the local tooling client may be seeded.
	 *
	 * <p>Default is "yes outside production, no in production": the tool client is a local
	 * operations/debugging convenience with the {@code svc.admin} scope, so a production deployment has to
	 * opt in explicitly ({@code TINYSTORE_TOOL_CLIENT_ENABLED=true} or the
	 * {@code tinystore.auth.tool-client.enabled} property) and then also supply a real secret.
	 */
	static boolean toolClientEnabled(Environment environment) {
		String configured = environment.getProperty(TOOL_ENABLED_PROPERTY);
		if (configured == null || configured.isBlank()) {
			configured = environment.getProperty(TOOL_ENABLED_ENV);
		}
		if (configured != null && !configured.isBlank()) {
			return Boolean.parseBoolean(configured.trim());
		}
		return !isProduction(environment);
	}

	/**
	 * Reads the secret from the {@link Environment} (its {@code systemEnvironment} property source is the
	 * process environment, so container env vars resolve here exactly as {@code System.getenv} would -- and
	 * the lookup stays testable).
	 */
	static String secretFrom(Environment environment, String name) {
		String secret = environment.getProperty(name);
		return (secret == null || secret.isBlank()) ? DEMO_SECRET : secret;
	}

	static boolean isDemoSecret(String secret) {
		return secret == null || secret.isBlank() || DEMO_SECRET.equals(secret);
	}
}
