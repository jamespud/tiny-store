package com.github.spud.tinystore.auth.application.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Review P2 / round-3 P1: a public default credential must not reach production.
 *
 * <p>{@code RegisteredClientConfig} seeds {@code tinystore-internal} with
 * {@code TINYSTORE_INTERNAL_CLIENT_SECRET} defaulting to {@code changeit}, and {@code tinystore-tool} with a
 * hardcoded {@code changeit}. That is fine for the demo/dev stack, where those values are also what the
 * compose files and the local tool use; under the {@code prod} profile it would publish a credential anyone
 * can read from the repository.
 *
 * <p>So: prod requires an explicit internal client secret (startup fails otherwise), and the tool client is
 * <b>not seeded at all</b> in prod unless it is explicitly enabled -- in which case it must also carry a real
 * secret. Note the seeding semantics: {@code seedIfAbsent} only inserts -- changing the environment variable
 * later does <b>not</b> rotate a secret that is already in the database; rotation needs an explicit update
 * path.
 */
@Component
public class DefaultCredentialGuard implements InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(DefaultCredentialGuard.class);

	private final Environment environment;

	public DefaultCredentialGuard(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void afterPropertiesSet() {
		if (!SeedCredentialPolicy.isProduction(environment)) {
			log.info("Non-production profile ({}): demo client secrets are allowed",
				String.join(",", environment.getActiveProfiles()));
			return;
		}

		if (SeedCredentialPolicy.isDemoSecret(
			SeedCredentialPolicy.secretFrom(environment, SeedCredentialPolicy.INTERNAL_SECRET_ENV))) {
			throw new IllegalStateException(SeedCredentialPolicy.INTERNAL_SECRET_ENV
				+ " must be set to a non-default value under the prod profile: the internal m2m client would "
				+ "otherwise be seeded with the public demo secret '" + SeedCredentialPolicy.DEMO_SECRET + "'");
		}

		if (!SeedCredentialPolicy.toolClientEnabled(environment)) {
			log.info("prod profile: the local tool client (tinystore-tool) is not seeded. Set "
				+ SeedCredentialPolicy.TOOL_ENABLED_ENV + "=true (plus "
				+ SeedCredentialPolicy.TOOL_SECRET_ENV + ") if a deployment really needs it");
			return;
		}

		if (SeedCredentialPolicy.isDemoSecret(
			SeedCredentialPolicy.secretFrom(environment, SeedCredentialPolicy.TOOL_SECRET_ENV))) {
			throw new IllegalStateException(SeedCredentialPolicy.TOOL_ENABLED_ENV + "=true under the prod "
				+ "profile requires " + SeedCredentialPolicy.TOOL_SECRET_ENV + " to be set to a non-default "
				+ "value: the admin-scoped tool client would otherwise be seeded with the public demo secret '"
				+ SeedCredentialPolicy.DEMO_SECRET + "'");
		}
		log.info("prod profile: the tool client is enabled with a non-default secret");
	}
}
