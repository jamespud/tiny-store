package com.github.spud.tinystore.infrastructure.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class FeignTraceHeaderInterceptorTest {

    private final FeignTraceHeaderInterceptor interceptor = new FeignTraceHeaderInterceptor();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void apply_withTraceIdInMdc_shouldPropagateAsHeader() {
        MDC.put(LogConstant.MDC_LOG_ID, "trace-123");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).containsEntry(LogConstant.HEADER_TRACE_ID,
            java.util.List.of("trace-123"));
    }

    @Test
    void apply_withoutTraceIdInMdc_shouldNotAddHeader() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(LogConstant.HEADER_TRACE_ID);
    }

    @Test
    void apply_withBlankTraceIdInMdc_shouldNotAddHeader() {
        MDC.put(LogConstant.MDC_LOG_ID, " ");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(LogConstant.HEADER_TRACE_ID);
    }
}
