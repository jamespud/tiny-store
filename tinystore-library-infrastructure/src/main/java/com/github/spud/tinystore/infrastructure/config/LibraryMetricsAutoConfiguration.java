package com.github.spud.tinystore.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared metrics auto-configuration: adds a common `application` tag to every meter
 * for all domains that include the library. Skipped if a domain already defines its
 * own MeterRegistryCustomizer (e.g. gateway).
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
public class LibraryMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistryCustomizer.class)
    MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            @Value("${spring.application.name:tinystore}") String applicationName) {
        return registry -> registry.config().commonTags("application", applicationName);
    }
}
