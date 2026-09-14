package com.github.spud.tinystore.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("tracing configuration guard (D1)")
class TracingConfigGuardTest {

    @Test
    @DisplayName("tracing on without an endpoint is refused instead of silently dropping spans")
    void tracingWithoutEndpointFailsFast() {
        assertThatThrownBy(() -> guard(true, "").afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("would drop every span silently");
        assertThatThrownBy(() -> guard(true, "   ").afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("tracing off (the default) or a configured endpoint is accepted")
    void validCombinationsAreAccepted() {
        assertThatCode(() -> guard(false, "").afterPropertiesSet()).doesNotThrowAnyException();
        assertThatCode(() -> guard(true, "http://zipkin:9411/api/v2/spans").afterPropertiesSet())
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unset tracing flag defaults to enabled, so the guard still applies")
    void missingFlagDefaultsToEnabled() {
        MockEnvironment environment = new MockEnvironment();
        assertThatThrownBy(() -> new TracingConfigGuard(environment).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class);
    }

    private static TracingConfigGuard guard(boolean tracingEnabled, String endpoint) {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("management.tracing.enabled", String.valueOf(tracingEnabled))
            .withProperty("management.zipkin.tracing.endpoint", endpoint);
        return new TracingConfigGuard(environment);
    }
}
