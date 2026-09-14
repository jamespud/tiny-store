package com.github.spud.tinystore.auth.application.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Review P2: a public default credential must not reach production.
 *
 * <p>{@code RegisteredClientConfig} seeds {@code tinystore-internal} with
 * {@code TINYSTORE_INTERNAL_CLIENT_SECRET} defaulting to {@code changeit}, and {@code tinystore-tool} with a
 * hardcoded {@code changeit}. That is fine for the demo/dev stack, where those values are also what the
 * compose files and the local tool use; under the {@code prod} profile it would publish a credential anyone
 * can read from the repository.
 *
 * <p>So: prod requires an explicit internal client secret (startup fails otherwise), and the tool client's
 * demo secret is at least called out loudly. Note the seeding semantics: {@code seedIfAbsent} only inserts --
 * changing the environment variable later does <b>not</b> rotate a secret that is already in the database;
 * rotation needs an explicit update path.
 */
@Component
public class DefaultCredentialGuard implements InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(DefaultCredentialGuard.class);

	private static final String DEMO_SECRET = "changeit";
	private static final String INTERNAL_SECRET_ENV = "TINYSTORE_INTERNAL_CLIENT_SECRET";

	private final Environment environment;

	public DefaultCredentialGuard(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void afterPropertiesSet() {
		if (!isProduction()) {
			log.info("Non-production profile ({}): demo client secrets are allowed",
				Arrays.toString(environment.getActiveProfiles()));
			return;
		}

		String secret = System.getenv(INTERNAL_SECRET_ENV);
		if (secret == null || secret.isBlank() || DEMO_SECRET.equals(secret)) {
			throw new IllegalStateException(INTERNAL_SECRET_ENV + " must be set to a non-default value under "
				+ "the prod profile: the internal m2m client would otherwise be seeded with the public demo "
				+ "secret '" + DEMO_SECRET + "'");
		}

		log.warn("prod profile: the local tool client (tinystore-tool) is still seeded with the demo secret "
			+ "'{}'; replace it before exposing this deployment, and note that seedIfAbsent does not rotate "
			+ "secrets that already exist in the database", DEMO_SECRET);
	}

	private boolean isProduction() {
		List<String> profiles = Arrays.asList(environment.getActiveProfiles());
		return profiles.contains("prod") || profiles.contains("production");
	}
}
