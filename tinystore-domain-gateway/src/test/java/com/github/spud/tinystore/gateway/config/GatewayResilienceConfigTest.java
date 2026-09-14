package com.github.spud.tinystore.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * The gateway settings that make the E1 read-failover guarantee true.
 *
 * <p>A retry filter is only half of "kill one replica, the reads survive": each attempt has to fail
 * fast, and the next attempt has to land on a *different* instance. Three properties carry that:
 *
 * <ul>
 * <li>{@code spring.cloud.loadbalancer.cache.ttl} -- how long a removed instance keeps being
 * returned by the load balancer (framework default 35s; this was the tail of the 20.6s failure
 * window in the original probe).</li>
 * <li>{@code spring.cloud.loadbalancer.nacos.enabled} -- the Nacos selector picks by
 * random weight, so with it on, one retry is a coin flip rather than a guaranteed move to the other
 * replica. The framework default (round-robin) is what {@code Retry=1,...} relies on. Turning this
 * on silently downgrades the failover guarantee to a probability, so it is pinned here.</li>
 * <li>{@code spring.cloud.gateway.server.webflux.httpclient.connect-timeout} -- a killed container
 * leaves an ip that black-holes SYNs instead of refusing them; without a bound the retry never
 * happens inside the caller's timeout.</li>
 * </ul>
 *
 * <p>These are the *defaults* (compose/k8s may override them); the live behaviour is asserted by
 * {@code make resilience-multi}, which kills a real replica under traffic.
 */
@DisplayName("gateway resilience configuration")
class GatewayResilienceConfigTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    @DisplayName("a removed instance stops being selected within 2s")
    void instanceListCacheTtlIsBounded() throws Exception {
        String ttl = requiredProperty("spring.cloud.loadbalancer.cache.ttl");

        assertThat(durationMillis(ttl))
            .withFailMessage("spring.cloud.loadbalancer.cache.ttl=%s keeps routing to an instance "
                + "Nacos already removed; the resilience probe's failure window is bounded by this "
                + "value, not by Nacos deregistration", ttl)
            .isLessThanOrEqualTo(2000L);
    }

    @Test
    @DisplayName("the selector stays deterministic so one retry always changes instance")
    void loadBalancerSelectorStaysDeterministic() throws Exception {
        Object nacosSelector = property("spring.cloud.loadbalancer.nacos.enabled");

        assertThat(nacosSelector == null || !"true".equals(String.valueOf(nacosSelector).trim()))
            .withFailMessage("spring.cloud.loadbalancer.nacos.enabled=%s switches the selector to "
                + "random-weight; `Retry=1,...` then only moves to another instance half the time "
                + "and make resilience-multi becomes flaky instead of a gate", nacosSelector)
            .isTrue();
    }

    @Test
    @DisplayName("a dead replica fails fast instead of black-holing the request")
    void connectAndResponseTimeoutsAreBounded() throws Exception {
        String connect = requiredProperty("spring.cloud.gateway.server.webflux.httpclient.connect-timeout");
        assertThat(Long.parseLong(connect.trim()))
            .withFailMessage("connect-timeout=%sms: a killed container's ip does not refuse "
                + "connections, so this value is the floor of the failover latency", connect)
            .isLessThanOrEqualTo(2000L);

        String response = requiredProperty("spring.cloud.gateway.server.webflux.httpclient.response-timeout");
        assertThat(durationMillis(response))
            .withFailMessage("response-timeout=%s must be a finite bound; unset means 'wait forever' "
                + "and hands the client a hung request instead of a retryable failure", response)
            .isLessThanOrEqualTo(30_000L);
    }

    @Test
    @DisplayName("no httpclient settings are left under the pre-4.x property prefix")
    void noDeadHttpClientPrefix() throws Exception {
        // Gateway 4.3 moved these to spring.cloud.gateway.server.webflux.httpclient; the old
        // spring.cloud.gateway.httpclient keys parse fine and do nothing at all, which is how the
        // stack ended up with no connect timeout despite the compose file "setting" one.
        assertThat(property("spring.cloud.gateway.httpclient"))
            .withFailMessage("spring.cloud.gateway.httpclient.* is inert in Gateway 4.x; use "
                + "spring.cloud.gateway.server.webflux.httpclient.*")
            .isNull();

        for (String compose : List.of("../docker/docker-compose-test.yml",
            "../docker/docker-compose-multi.yml")) {
            Path path = Paths.get(compose);
            assertThat(path)
                .withFailMessage("cannot check %s for inert gateway httpclient settings", path.toAbsolutePath())
                .exists();
            // Only actual settings count -- the files are allowed to *mention* the old prefix when
            // explaining why it was removed.
            List<String> inert = Files.readAllLines(path).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("SPRING_CLOUD_GATEWAY_HTTPCLIENT_"))
                .toList();
            assertThat(inert)
                .withFailMessage("%s still sets %s, which Gateway 4.x ignores (the prefix is "
                    + "spring.cloud.gateway.server.webflux.httpclient) -- the setting looks configured "
                    + "and does nothing", compose, inert)
                .isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static String requiredProperty(String key) throws Exception {
        Object value = property(key);
        assertThat(value)
            .withFailMessage("%s is not set in the gateway's application.yml", key)
            .isNotNull();
        return defaultValue(String.valueOf(value));
    }

    /**
     * Resolves the default of a {@code ${ENV:default}} placeholder; a plain literal is returned as is.
     * Only the default matters here, because that is what a deployment which sets no override gets.
     */
    private static String defaultValue(String raw) {
        String value = raw.trim();
        if (value.startsWith("${") && value.endsWith("}") && value.contains(":")) {
            return value.substring(value.indexOf(':') + 1, value.length() - 1).trim();
        }
        return value;
    }

    /** Reads a dotted key out of the packaged application.yml, or null when absent. */
    private static Object property(String key) throws Exception {
        Object current;
        try (InputStream in = GatewayResilienceConfigTest.class.getClassLoader()
            .getResourceAsStream("application.yml")) {
            assertThat(in).withFailMessage("application.yml missing from the classpath").isNotNull();
            current = YAML.readValue(in, Map.class);
        }
        for (String segment : key.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Resolves the default of a {@code ${ENV:default}} placeholder; a plain literal is returned as is.
     * Only the default matters here, because that is what a deployment which sets no override gets.
     */
    private static long durationMillis(String raw) {
        String value = defaultValue(raw);
        if (value.endsWith("ms")) {
            return Long.parseLong(value.substring(0, value.length() - 2));
        }
        if (value.endsWith("s")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 1000L;
        }
        return Duration.parse(value).toMillis();
    }
}
