package com.github.spud.tinystore.infrastructure.config;

import com.github.spud.tinystore.infrastructure.feign.FeignTraceHeaderInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Feign trace header interceptor for every domain that includes the
 * library and has OpenFeign on the classpath. All Feign clients in the application
 * automatically propagate the MDC trace id via X-Trace-ID.
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignTraceAutoConfiguration {

    @Bean
    FeignTraceHeaderInterceptor feignTraceHeaderInterceptor() {
        return new FeignTraceHeaderInterceptor();
    }
}
