package com.github.spud.tinystore.auth.application.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Review P2: the demo secrets must not silently become production credentials. The env-var part is not
 * simulated here (the guard reads it exactly like {@code RegisteredClientConfig} does); these cases pin the
 * profile gate and the fail-fast behaviour.
 */
@DisplayName("auth default-credential guard (P2)")
class DefaultCredentialGuardTest {

    @Test
    @DisplayName("prod without an injected internal secret fails fast")
    void prodRequiresInternalSecret() {
        // The test JVM has no TINYSTORE_INTERNAL_CLIENT_SECRET set, so prod must refuse to start.
        assertThatThrownBy(() -> guard("prod").afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TINYSTORE_INTERNAL_CLIENT_SECRET");
    }

    @Test
    @DisplayName("dev/local keep working with the demo secrets")
    void nonProductionProfilesAreAllowed() {
        assertThatCode(() -> guard("dev").afterPropertiesSet()).doesNotThrowAnyException();
        assertThatCode(() -> guard().afterPropertiesSet()).doesNotThrowAnyException();
    }

    private static DefaultCredentialGuard guard(String... profiles) {
        return new DefaultCredentialGuard(new MockEnvironment().withProperty(
            "spring.profiles.active", String.join(",", profiles)));
    }
}
