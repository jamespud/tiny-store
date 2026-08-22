package com.github.spud.tinystore.infrastructure.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Smoke test verifying that the E2 traced dependencies are on the classpath and
 * their core SPI types are loadable. This guards against a silent regression where
 * the micrometer-tracing bridge or Brave reporter is removed but config stays.
 */
class TracingDependencySmokeTest {

    @Test
    void micrometerTracingAndBraveTypes_shouldBeLoadable() throws Exception {
        // Micrometer's tracing SPI (bridge-agnostic)
        Class<?> tracerSpi = Class.forName("io.micrometer.tracing.Tracer");
        assertThat(tracerSpi).isNotNull();
        Class<?> spanSpi = Class.forName("io.micrometer.tracing.Span");
        assertThat(spanSpi).isNotNull();

        // Brave bridge + reporter (the E2 backend)
        Class<?> braveTracer = Class.forName("brave.Tracer");
        assertThat(braveTracer).isNotNull();
        Class<?> braveSpan = Class.forName("brave.Span");
        assertThat(braveSpan).isNotNull();
        Class<?> reporter = Class.forName("zipkin2.reporter.brave.AsyncZipkinSpanHandler");
        assertThat(reporter).isNotNull();
    }
}
