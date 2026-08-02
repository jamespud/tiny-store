package com.github.spud.tinystore.infrastructure.feign;

import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * Propagates the current request's trace id to downstream services over Feign.
 *
 * Reads {@link LogConstant#MDC_LOG_ID} from the MDC (populated by each service's
 * logging filter/interceptor from the incoming X-Trace-ID header) and writes it back
 * to the outgoing {@link LogConstant#HEADER_TRACE_ID} header, so log lines across
 * order -> inventory / promotion stay correlated under one trace id. When the MDC is
 * empty (e.g. scheduler threads), no header is written and the downstream service
 * generates its own id.
 */
public class FeignTraceHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(LogConstant.MDC_LOG_ID);
        if (StringUtils.hasText(traceId)) {
            template.header(LogConstant.HEADER_TRACE_ID, traceId);
        }
    }
}
