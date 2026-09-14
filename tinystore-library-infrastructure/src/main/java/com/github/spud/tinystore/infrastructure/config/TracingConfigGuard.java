package com.github.spud.tinystore.infrastructure.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * D1: refuse a tracing configuration that can only lose data.
 *
 * <p>Boot 3.5 creates the Zipkin sender and span handler from {@code management.tracing.enabled}
 * alone (there is no per-backend switch; checked against {@code /actuator/beans}), and the endpoint
 * comes from {@code management.zipkin.tracing.endpoint}. So "tracing on, endpoint empty" starts
 * cleanly and then drops every span without a word -- the audit found exactly that shape with the
 * default {@code ZIPKIN_ENDPOINT=""}.
 *
 * <p>The default in every service is now {@code management.tracing.enabled=false} (tracing is
 * opt-in), which is enough to stop the silent loss. This guard covers the remaining half: when
 * somebody *does* turn tracing on, they must also say where the spans go, and getting that wrong is a
 * startup failure instead of a silent one.
 */
// Registered through META-INF/spring/...AutoConfiguration.imports, not component scanning: the
// domains do not scan this package, which is exactly why a @Component version of this guard never
// ran (checked at runtime before switching to an auto-configuration).
@Configuration
public class TracingConfigGuard implements InitializingBean {

	private static final String TRACING_ENABLED = "management.tracing.enabled";
	private static final String ZIPKIN_ENDPOINT = "management.zipkin.tracing.endpoint";

	private final Environment environment;

	public TracingConfigGuard(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void afterPropertiesSet() {
		boolean tracingEnabled = environment.getProperty(TRACING_ENABLED, Boolean.class, true);
		String endpoint = environment.getProperty(ZIPKIN_ENDPOINT, "");
		if (tracingEnabled && !StringUtils.hasText(endpoint)) {
			throw new IllegalStateException("Tracing is enabled (" + TRACING_ENABLED + "=true) but "
				+ ZIPKIN_ENDPOINT + " is empty: the zipkin reporter would drop every span silently. Set "
				+ "the endpoint (e.g. http://zipkin:9411/api/v2/spans) or leave tracing disabled.");
		}
	}
}
