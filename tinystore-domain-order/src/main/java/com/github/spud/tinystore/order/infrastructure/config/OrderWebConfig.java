package com.github.spud.tinystore.order.infrastructure.config;

import com.github.spud.tinystore.interfaces.aspect.LogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the shared {@link LogInterceptor} for the order service.
 *
 * The order application does not component-scan the shared library package (unlike
 * inventory/auth/product), so the library's WebLoConfig is not picked up here. Without
 * this interceptor the MDC logId stays empty, which would break X-Trace-ID propagation
 * over Feign (the trace header is read from the MDC).
 */
@Configuration
public class OrderWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LogInterceptor())
            .addPathPatterns("/**")
            .excludePathPatterns("/static/**", "/favicon.ico", "/error");
    }
}
